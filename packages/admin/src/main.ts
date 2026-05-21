import {
  createDatabasePool,
  runMigrations,
  EnvironmentConfig,
  createRootLogger,
  RedisCacheService,
  DomainEventPublisher,
  DiscordJsRuntimeGateway,
  initializeContainer,
  container,
  Ok,
  Err,
  DomainError,
} from '@ltdjms/shared';
import { configureEconomyContainer, ECONOMY_TOKENS, CurrencyTransactionSource } from '@ltdjms/economy';
import { configureDispatchContainer, DISPATCH_TOKENS } from '@ltdjms/dispatch';
import {
  configureContainer as configureShopContainer,
  SHOP_TOKENS,
  FiatOrderProcessingScheduler,
  EcpayCallbackHttpServer,
} from '@ltdjms/shop';
import type { ProductRewardService } from '@ltdjms/shop';
import { initializeAIModule, AI_TOKENS, AIChatMentionListener } from '@ltdjms/ai';
import {
  configureAdminContainer,
} from './di/AdminModule.js';
import type {
  EscortDispatchHandoffService,
  BalanceService,
  BalanceAdjustmentService,
  CurrencyTransactionService,
} from '@ltdjms/shop';
import { Client, GatewayIntentBits } from 'discord.js';
import { drizzle } from 'drizzle-orm/node-postgres';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

async function main(): Promise<void> {
  const logger = createRootLogger();

  // 1. Config — load and validate
  const config = new EnvironmentConfig(process.cwd(), process.env, logger);
  config.parse();

  // 2. Database pool + migration
  const pool = await createDatabasePool({
    url: config.getDatabaseUrl(),
    max: config.getPoolMaxSize(),
    connectionTimeoutMillis: config.getPoolConnectionTimeout(),
    idleTimeoutMillis: config.getPoolIdleTimeout(),
  });
  const migrationsDir = join(__dirname, '..', '..', 'shared', 'db', 'migrations');
  await runMigrations(pool, migrationsDir, logger);
  const db: NodePgDatabase = drizzle(pool) as unknown as NodePgDatabase;

  // 3. Redis cache
  const cacheService = new RedisCacheService(config.getRedisUri(), logger);

  // 4. Domain event system
  const eventPublisher = new DomainEventPublisher(logger);

  // 5. Discord runtime gateway (client will be set after login)
  const runtimeGateway = new DiscordJsRuntimeGateway();

  // 6. Initialize shared DI container
  initializeContainer({
    config,
    cacheService,
    eventPublisher,
    runtimeGateway,
    logger,
    databasePool: pool,
  });

  // 7. Economy module
  configureEconomyContainer();

  // 8. Dispatch module
  configureDispatchContainer();

  // 9. Shop module prerequisites
  const balanceService = container.resolve<BalanceService>(ECONOMY_TOKENS.BalanceService);
  const balanceAdjustmentService = container.resolve<BalanceAdjustmentService>(ECONOMY_TOKENS.BalanceAdjustmentService);
  const currencyTransactionService = container.resolve<CurrencyTransactionService>(ECONOMY_TOKENS.CurrencyTransactionService);
  const escortDispatchHandoffService = container.resolve<EscortDispatchHandoffService>(DISPATCH_TOKENS.EscortDispatchHandoffService);

  // ProductRewardService adapter wrapping GameRewardService.creditReward
  // into the grantReward interface expected by the shop module.
  const gameRewardService = container.resolve<import('@ltdjms/economy').GameRewardService>(ECONOMY_TOKENS.GameRewardService);
  const productRewardService: ProductRewardService = {
    async grantReward(request: Parameters<ProductRewardService['grantReward']>[0]) {
      try {
        const newBalance = await gameRewardService.creditReward(
          request.guildId,
          request.userId,
          request.amount,
          CurrencyTransactionSource.PRODUCT_REWARD,
        );
        return new Ok({ amount: request.amount, currencyBalanceAfter: newBalance });
      } catch (e) {
        return new Err(
          DomainError.unexpectedFailure(e instanceof Error ? e.message : String(e)),
        );
      }
    },
  };

  configureShopContainer({
    db,
    productRewardService,
    escortDispatchHandoffService,
    balanceService,
    balanceAdjustmentService,
    currencyTransactionService,
    logger,
  });

  // 10. AI module
  initializeAIModule();

  // 11. Discord client — login and wait for ready
  const client = new Client({
    intents: [
      GatewayIntentBits.Guilds,
      GatewayIntentBits.GuildMessages,
      GatewayIntentBits.MessageContent,
    ],
  });

  const clientReady = new Promise<void>((resolve) => {
    client.once('ready', () => resolve());
  });

  await client.login(config.getDiscordBotToken());
  await clientReady;

  // 12. Publish ready to gateway (enables requireReadyClient for downstream consumers)
  runtimeGateway.publishReady(client);

  // 13. Admin module — registers all handlers and wires interactionCreate
  // Must happen after client is ready (configureAdminContainer calls listen()).
  configureAdminContainer();

  // 14. Wire AI message listener
  const aiListener = container.resolve<AIChatMentionListener>(AI_TOKENS.AIChatMentionListener);
  client.on('messageCreate', (msg) => aiListener.onMessageCreate(msg));

  // 15. Start background services
  const scheduler = container.resolve<FiatOrderProcessingScheduler>(SHOP_TOKENS.FiatOrderProcessingScheduler);
  scheduler.start();

  const callbackServer = container.resolve<EcpayCallbackHttpServer>(SHOP_TOKENS.EcpayCallbackHttpServer);
  callbackServer.start();

  // 16. Shutdown hook
  process.on('SIGTERM', async () => {
    logger.info('Received SIGTERM — shutting down...');
    scheduler.stop();
    await callbackServer.stop();
    await cacheService.shutdown();
    client.destroy();
    await pool.end();
    logger.info('Shutdown complete');
    process.exit(0);
  });

  process.on('SIGINT', async () => {
    logger.info('Received SIGINT — shutting down...');
    process.emit('SIGTERM');
  });

  logger.info('LTDJMS started successfully');
}

main().catch((err) => {
  console.error('Failed to start LTDJMS', err);
  process.exit(1);
});
