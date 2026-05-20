import { createRootLogger } from './infra/logger/logger.js';
import { EnvironmentConfig } from './infra/config/environment-config.js';
import { createDatabasePool } from './infra/database/connection.js';
import { runMigrations } from './infra/database/migration-runner.js';
import { RedisCacheService } from './infra/cache/redis-cache-service.js';
import { DomainEventPublisher } from './infra/events/domain-event-publisher.js';
import { DiscordJsRuntimeGateway } from './discord/services/discord-js-runtime-gateway.js';
import { initializeContainer, container } from './infra/di/index.js';
import { Client, GatewayIntentBits, Events } from 'discord.js';
/**
 * Main entry point for the application.
 * Startup sequence mirroring Java DiscordCurrencyBot:
 * 1. Load config
 * 2. Create logger
 * 3. Create DB pool + run migrations
 * 4. Create Redis client
 * 5. Create DomainEventPublisher
 * 6. Create discord.js Client
 * 7. Publish ready to DiscordRuntimeGateway
 * 8. Login
 * 9. Register shutdown hook
 */
export async function main() {
    // 1. Load config
    const config = new EnvironmentConfig();
    const validatedConfig = config.parse();
    const logger = createRootLogger('info');
    logger.info('Configuration loaded successfully');
    // 2. Create DB pool + run migrations
    logger.info('Connecting to database...');
    const pool = await createDatabasePool({
        url: config.getDatabaseUrl(),
        max: config.getPoolMaxSize(),
        connectionTimeoutMillis: config.getPoolConnectionTimeout(),
        idleTimeoutMillis: config.getPoolIdleTimeout(),
    });
    logger.info('Database connected');
    // Run migrations
    const migrationsDir = './db/migrations';
    try {
        await runMigrations(pool, migrationsDir);
        logger.info('Database migrations completed');
    }
    catch (err) {
        logger.error({ err }, 'Database migration failed');
        throw err;
    }
    // 3. Create Redis client
    const redisUri = config.getRedisUri();
    const cacheService = new RedisCacheService(redisUri);
    logger.info('Redis cache service initialized');
    // 4. Create DomainEventPublisher
    const eventPublisher = new DomainEventPublisher();
    logger.info('Domain event publisher initialized');
    // 5. Initialize shared DI container
    const runtimeGateway = new DiscordJsRuntimeGateway();
    initializeContainer({
        config,
        cacheService,
        eventPublisher,
        runtimeGateway,
        logger,
        databasePool: pool,
    });
    // 6. Create discord.js Client
    const client = new Client({
        intents: [
            GatewayIntentBits.Guilds,
            GatewayIntentBits.GuildMessages,
            GatewayIntentBits.MessageContent,
            GatewayIntentBits.GuildMembers,
        ],
    });
    client.once('ready', async () => {
        runtimeGateway.publishReady(client);
        logger.info({ user: client.user?.tag }, 'Discord bot logged in successfully');
        // Initialize all module containers and wire Discord event handlers
        try {
            await initializeAllModules(pool, eventPublisher, client, config, logger);
            logger.info('All module containers initialized');
        }
        catch (err) {
            logger.error({ err }, 'Failed to initialize module containers');
        }
    });
    client.on('error', (err) => {
        logger.error({ err }, 'Discord client error');
    });
    // 7. Login to Discord
    const token = config.getDiscordBotToken();
    await client.login(token);
    logger.info('Discord bot login initiated');
    // 8. Register shutdown hook
    const shutdown = async () => {
        logger.info('Shutting down...');
        try {
            client.destroy();
            await cacheService.shutdown();
            await pool.end();
            logger.info('Shutdown complete');
        }
        catch (err) {
            logger.error({ err }, 'Error during shutdown');
        }
        process.exit(0);
    };
    process.on('SIGTERM', shutdown);
    process.on('SIGINT', shutdown);
    logger.info('Application startup complete');
}
/**
 * Dynamically imports and initializes all module DI containers.
 * Uses dynamic imports to avoid compile-time circular dependencies
 * between shared and other workspace packages.
 *
 * Initialization order respects dependency graph:
 * economy -> dispatch -> shop -> ai -> admin
 */
async function initializeAllModules(pool, eventPublisher, client, config, logger) {
    // ---- 1. Economy ----
    const { configureEconomyContainer, ECONOMY_TOKENS: econTokens } = await import('@ltdjms/economy');
    configureEconomyContainer();
    logger.info('Economy module initialized');
    // ---- 2. Dispatch ----
    const { configureDispatchContainer, DISPATCH_TOKENS: dispTokens } = await import('@ltdjms/dispatch');
    configureDispatchContainer();
    logger.info('Dispatch module initialized');
    // Resolve services needed by shop from economy and dispatch containers
    const balanceService = container.resolve(econTokens.BalanceService);
    const balanceAdjustmentService = container.resolve(econTokens.BalanceAdjustmentService);
    const currencyTransactionService = container.resolve(econTokens.CurrencyTransactionService);
    const productRewardService = container.resolve(econTokens.GameRewardService);
    const escortHandoffService = container.resolve(dispTokens.EscortDispatchHandoffService);
    // ---- 3. Shop ----
    const { configureContainer: configureShopContainer, DrizzleProductRepository, DrizzleRedemptionTransactionService, } = await import('@ltdjms/shop');
    const productRepo = new DrizzleProductRepository(pool);
    const redemptionTxService = new DrizzleRedemptionTransactionService(pool);
    configureShopContainer({
        db: pool,
        productRepository: productRepo,
        productRewardService: productRewardService,
        escortDispatchHandoffService: escortHandoffService,
        balanceService: balanceService,
        balanceAdjustmentService: balanceAdjustmentService,
        currencyTransactionService: currencyTransactionService,
        redemptionTransactionService: redemptionTxService,
        logger,
    });
    logger.info('Shop module initialized');
    // ---- 4. AI ----
    const { initializeAIModule, AI_TOKENS } = await import('@ltdjms/ai');
    initializeAIModule();
    logger.info('AI module initialized');
    // ---- 5. Admin ----
    const { configureAdminContainer, ADMIN_TOKENS, SlashCommandListener, SlashCommandRegistrar } = await import('@ltdjms/admin');
    configureAdminContainer();
    const slashCommandListener = container.resolve(ADMIN_TOKENS.SlashCommandListener);
    // Wire Discord events via shared bootstrap
    const aiChatListener = container.resolve(AI_TOKENS.AIChatMentionListener);
    await bootstrapDiscordHandlers(client, slashCommandListener, aiChatListener, SlashCommandRegistrar, logger);
    logger.info('Admin module initialized');
}
/**
 * Shared bootstrap function for wiring Discord event handlers.
 *
 * TODO: Each module should register its own handlers via DI rather than
 * relying on shared wiring in this bootstrap function. This approach is
 * temporary until all modules have their own container configuration
 * and can self-register their event listeners.
 */
async function bootstrapDiscordHandlers(client, slashCommandListener, aiChatListener, slashCommandRegistrar, logger) {
    // Wire Discord interactionCreate event to the slash command listener
    client.on(Events.InteractionCreate, async (interaction) => {
        if (!interaction.guildId)
            return;
        let type;
        let commandNameOrCustomId;
        if (interaction.isChatInputCommand()) {
            type = 'chatInput';
            commandNameOrCustomId = interaction.commandName;
        }
        else if (interaction.isButton()) {
            type = 'button';
            commandNameOrCustomId = interaction.customId;
        }
        else if (interaction.isStringSelectMenu()) {
            type = 'stringSelect';
            commandNameOrCustomId = interaction.customId;
        }
        else if (interaction.isModalSubmit()) {
            type = 'modalSubmit';
            commandNameOrCustomId = interaction.customId;
        }
        else {
            return;
        }
        await slashCommandListener.onInteraction(interaction, { guildId: interaction.guildId, userId: interaction.user.id }, type, commandNameOrCustomId);
    });
    // Wire Discord messageCreate event to the AI chat listener
    client.on(Events.MessageCreate, async (message) => {
        await aiChatListener.onMessageCreate(message);
    });
    // Register slash commands with Discord REST API
    if (client.user) {
        const result = await slashCommandRegistrar.registerAll(client.user.id, async (route, body) => {
            return client.rest.put(route, { body });
        });
        logger.info({ result }, 'Slash commands registered');
    }
}
// Allow direct execution
if (process.argv[1] && import.meta.url.endsWith(process.argv[1])) {
    main().catch((err) => {
        console.error('Fatal startup error:', err);
        process.exit(1);
    });
}
//# sourceMappingURL=main.js.map