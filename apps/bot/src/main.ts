import 'reflect-metadata';

import {
  createRootLogger,
  EnvironmentConfig,
  createDatabasePool,
  runMigrations,
  RedisCacheService,
  DomainEventPublisher,
  initializeContainer,
  container,
  DiscordJsRuntimeGateway,
  DiscordJsEmbedBuilder,
  Ok,
  Err,
  DomainError,
} from '@ltdjms/shared';
import {
  configureEconomyContainer,
  ECONOMY_TOKENS,
  CurrencyTransactionSource,
  type GameRewardService,
} from '@ltdjms/economy';
import { configureDispatchContainer, DISPATCH_TOKENS } from '@ltdjms/dispatch';
import {
  configureContainer as configureShopContainer,
  SHOP_TOKENS,
  FiatOrderProcessingScheduler,
  EcpayCallbackHttpServer,
  type ProductRewardService,
  type BalanceService,
  type BalanceAdjustmentService,
  type EscortDispatchHandoffService,
} from '@ltdjms/shop';
import {
  initializeAIModule,
  AI_TOKENS,
  type AIChatMentionListener,
} from '@ltdjms/ai';
import {
  configureAdminContainer,
  ADMIN_TOKENS,
  SlashCommandRegistrar,
  disposeAdminContainer,
} from '@ltdjms/admin';
import { Client, GatewayIntentBits } from 'discord.js';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';

/**
 * Main entry point for the application.
 * Startup sequence mirroring Java DiscordCurrencyBot:
 * 1. Load config
 * 2. Create logger
 * 3. Create DB pool + run migrations
 * 4. Create drizzle ORM wrapper
 * 5. Create Redis client
 * 6. Create DomainEventPublisher
 * 7. Create DiscordJsRuntimeGateway + initialize DI container
 * 8. Economy module
 * 9. Dispatch module
 * 10. Shop module (with ProductRewardService adapter)
 * 11. AI module
 * 12. Create discord.js Client + login
 * 13. Publish ready to DiscordRuntimeGateway
 * 14. Admin module (wires interactionCreate via DI)
 * 15. Wire AI message listener
 * 16. Register slash commands with Discord API
 * 17. Start background services (scheduler, callback server)
 * 18. Register shutdown hook
 */
export async function main(): Promise<void> {
  // 1. Config + Logger
  const config = new EnvironmentConfig();
  config.parse();
  const logger = createRootLogger();

  logger.info('Configuration loaded successfully');

  // 2. Database pool + migration
  logger.info('Connecting to database...');
  const pool = await createDatabasePool({
    url: config.getDatabaseUrl(),
    max: config.getPoolMaxSize(),
    connectionTimeoutMillis: config.getPoolConnectionTimeout(),
    idleTimeoutMillis: config.getPoolIdleTimeout(),
  });
  logger.info('Database connected');

  const migrationsDir = './db/migrations';
  try {
    await runMigrations(pool, migrationsDir);
    logger.info('Database migrations completed');
  } catch (err) {
    logger.error({ err }, 'Database migration failed');
    throw err;
  }

  const db: NodePgDatabase = drizzle(pool) as unknown as NodePgDatabase;

  // 3. Redis cache
  const cacheService = new RedisCacheService(config.getRedisUri());
  logger.info('Redis cache service initialized');

  // 4. Domain event publisher
  const eventPublisher = new DomainEventPublisher();
  logger.info('Domain event publisher initialized');

  // 5. Discord runtime gateway + embed builder
  const runtimeGateway = new DiscordJsRuntimeGateway();
  const discordEmbedBuilder = new DiscordJsEmbedBuilder(logger);

  // 6. Initialize shared DI container
  initializeContainer({
    config,
    cacheService,
    eventPublisher,
    runtimeGateway,
    embedBuilder: discordEmbedBuilder,
    logger,
    databasePool: pool,
  });

  // 7. Economy module
  configureEconomyContainer();
  logger.info('Economy module initialized');

  // 8. Dispatch module
  configureDispatchContainer();
  logger.info('Dispatch module initialized');

  // 9. Shop module — resolves economy/dispatch services via DI,
  //    wraps GameRewardService into ProductRewardService adapter,
  //    then configures the shop container with drizzle-wrapped db.
  const balanceService = container.resolve<BalanceService>(
    ECONOMY_TOKENS.BalanceService,
  );
  const balanceAdjustmentService = container.resolve<BalanceAdjustmentService>(
    ECONOMY_TOKENS.BalanceAdjustmentService,
  );
  const escortDispatchHandoffService = container.resolve<EscortDispatchHandoffService>(
    DISPATCH_TOKENS.EscortDispatchHandoffService,
  );

  // ProductRewardService adapter wrapping GameRewardService.creditReward
  // into the grantReward interface expected by the shop module.
  const gameRewardService = container.resolve<GameRewardService>(
    ECONOMY_TOKENS.GameRewardService,
  );
  const productRewardService: ProductRewardService = {
    async grantReward(request) {
      const result = await gameRewardService.creditReward(
        request.guildId,
        request.userId,
        request.amount,
        CurrencyTransactionSource.PRODUCT_REWARD,
      );
      if (result.isErr()) {
        return new Err(result.getError());
      }
      return new Ok({ amount: request.amount, currencyBalanceAfter: result.getValue().newBalance });
    },
  };

  configureShopContainer({
    db,
    productRewardService,
    escortDispatchHandoffService,
    balanceService,
    balanceAdjustmentService,
    logger,
  });
  logger.info('Shop module initialized');

  // 11. Discord client
  const client = new Client({
    intents: [
      GatewayIntentBits.Guilds,
      GatewayIntentBits.GuildMessages,
      GatewayIntentBits.MessageContent,
      GatewayIntentBits.GuildMembers,
    ],
  });

  client.on('error', (err) => {
    logger.error({ err }, 'Discord client error');
  });

  // 12. Login and wait for ready
  const clientReady = new Promise<void>((resolve) => {
    client.once('ready', () => resolve());
  });

  await client.login(config.getDiscordBotToken());
  await clientReady;

  // 13. Publish ready to gateway (enables requireReadyClient for downstream consumers)
  runtimeGateway.publishReady(client);
  logger.info({ user: client.user?.tag }, 'Discord bot logged in successfully');

  // 14. AI module — must be after publishReady (needs selfUserId via DiscordRuntimeGateway)
  initializeAIModule();
  logger.info('AI module initialized');

  // 15. Admin module — wires all handlers and interactionCreate via DI.
  //     Must happen after client is ready (listen() calls requireReadyClient()).
  configureAdminContainer();
  logger.info('Admin module initialized');

  // 16. Wire AI message listener
  const aiListener = container.resolve<AIChatMentionListener>(AI_TOKENS.AIChatMentionListener);
  client.on('messageCreate', (msg) => aiListener.onMessageCreate(msg));

  // 17. Register slash commands with Discord API
  if (client.user) {
    const result = await SlashCommandRegistrar.registerAll(
      client.user.id,
      async (route: string, body: unknown) => {
        return client.rest.put(route as `/${string}`, { body });
      },
    );
    logger.info({ result }, 'Slash commands registered');
  }

  // 18. Start background services
  const scheduler = container.resolve<FiatOrderProcessingScheduler>(
    SHOP_TOKENS.FiatOrderProcessingScheduler,
  );
  scheduler.start();

  const callbackServer = container.resolve<EcpayCallbackHttpServer>(
    SHOP_TOKENS.EcpayCallbackHttpServer,
  );
  callbackServer.start();

  logger.info('Application startup complete');

  // 19. Shutdown hook
  const shutdown = async () => {
    logger.info('Shutting down...');
    scheduler.stop();
    await callbackServer.stop();
    disposeAdminContainer();
    client.destroy();
    await cacheService.shutdown();
    await pool.end();
    logger.info('Shutdown complete');
    process.exit(0);
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', () => {
    process.emit('SIGTERM');
  });
}

// Allow direct execution
if (process.argv[1] && import.meta.url.endsWith(process.argv[1])) {
  main().catch((err) => {
    console.error('Fatal startup error:', err);
    process.exit(1);
  });
}
