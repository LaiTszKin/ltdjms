import { type DiscordInteraction } from '../domain/discord-interaction.js';
/**
 * Mock implementation of DiscordInteraction for testing.
 * Records all calls for verification.
 * Matches Java MockDiscordInteraction.
 */
export declare class MockDiscordInteraction implements DiscordInteraction {
    private readonly _guildId;
    private readonly _userId;
    private readonly _channelId;
    private readonly _ephemeral;
    private _acknowledged;
    private readonly _replyMessages;
    private readonly _replyEmbeds;
    private readonly _editedEmbeds;
    private _deferReplyCount;
    constructor(guildId: string, userId: string, channelId?: string, ephemeral?: boolean);
    getGuildId(): string;
    getUserId(): string;
    getChannelId(): string;
    isEphemeral(): boolean;
    reply(message: string): Promise<void>;
    replyEmbed(embed: unknown): Promise<void>;
    editEmbed(embed: unknown): Promise<void>;
    deferReply(): Promise<void>;
    getHook(): unknown;
    isAcknowledged(): boolean;
    getReplyMessages(): string[];
    getReplyEmbeds(): unknown[];
    getEditedEmbeds(): unknown[];
    getDeferReplyCount(): number;
    getReplyCount(): number;
    getReplyEmbedCount(): number;
    getEditEmbedCount(): number;
    hasReplies(): boolean;
    hasDeferred(): boolean;
    clear(): void;
}
