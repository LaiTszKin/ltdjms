import { type DiscordContext } from '../domain/discord-context.js';
/**
 * Mock implementation of DiscordContext for testing.
 * Allows setting and inspecting context values without a real Discord event.
 * Matches Java MockDiscordContext.
 */
export declare class MockDiscordContext implements DiscordContext {
    private readonly _guildId;
    private readonly _userId;
    private readonly _channelId;
    private readonly _userMention;
    private readonly options;
    constructor(guildId: number, userId: number, channelId: number, userMention: string);
    getGuildId(): number;
    getUserId(): number;
    getChannelId(): number;
    getUserMention(): string;
    getOption(name: string): string | null;
    getOptionAsString(name: string): string | null;
    getOptionAsNumber(name: string): number | null;
    setOption(name: string, value: unknown): void;
    clearOption(name: string): void;
    clearAllOptions(): void;
    hasOption(name: string): boolean;
    getOptionCount(): number;
}
