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
export declare function main(): Promise<void>;
