import { createRootLogger } from './infra/logger/logger.js';
import { EnvironmentConfig } from './infra/config/environment-config.js';
import { createDatabasePool } from './infra/database/connection.js';
import { runMigrations } from './infra/database/migration-runner.js';
import { RedisCacheService } from './infra/cache/redis-cache-service.js';
import { DomainEventPublisher } from './infra/events/domain-event-publisher.js';
import { DiscordJsRuntimeGateway } from './discord/services/discord-js-runtime-gateway.js';
import { Client, GatewayIntentBits } from 'discord.js';
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
    // 5. Create discord.js Client
    const runtimeGateway = new DiscordJsRuntimeGateway();
    const client = new Client({
        intents: [
            GatewayIntentBits.Guilds,
            GatewayIntentBits.GuildMessages,
            GatewayIntentBits.MessageContent,
            GatewayIntentBits.GuildMembers,
        ],
    });
    client.once('ready', () => {
        runtimeGateway.publishReady(client);
        logger.info({ user: client.user?.tag }, 'Discord bot logged in successfully');
    });
    client.on('error', (err) => {
        logger.error({ err }, 'Discord client error');
    });
    // 6. Login to Discord
    const token = config.getDiscordBotToken();
    await client.login(token);
    logger.info('Discord bot login initiated');
    // 7. Register shutdown hook
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
// Allow direct execution
if (process.argv[1] && import.meta.url.endsWith(process.argv[1])) {
    main().catch((err) => {
        console.error('Fatal startup error:', err);
        process.exit(1);
    });
}
//# sourceMappingURL=main.js.map