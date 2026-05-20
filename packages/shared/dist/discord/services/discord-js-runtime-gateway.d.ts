import { type DiscordRuntimeGateway } from '../domain/discord-runtime-gateway.js';
/**
 * Thrown when Discord runtime access is requested before publishReady.
 * Matches Java DiscordRuntimeNotReadyException.
 */
export declare class DiscordRuntimeNotReadyError extends Error {
    constructor(message?: string);
}
/**
 * Discord.js implementation of DiscordRuntimeGateway.
 * Uses AtomicRef<Client> pattern — bootstrap calls publishReady after client login.
 */
export declare class DiscordJsRuntimeGateway implements DiscordRuntimeGateway {
    private clientRef;
    isReady(): boolean;
    publishReady(client: unknown): void;
    requireReadyClient(): unknown;
    findGuild(guildId: string): unknown | null;
    findGuildChannel(guildId: string, channelId: string): unknown | null;
    selfUserId(): string;
}
