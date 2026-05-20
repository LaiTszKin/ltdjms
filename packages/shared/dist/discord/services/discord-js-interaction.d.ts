import { type CommandInteraction, type ButtonInteraction, type ModalSubmitInteraction } from 'discord.js';
import { type DiscordInteraction } from '../domain/discord-interaction.js';
/**
 * Discord.js implementation of DiscordInteraction.
 * Wraps CommandInteraction / ButtonInteraction / ModalSubmitInteraction.
 */
export declare class DiscordJsInteraction implements DiscordInteraction {
    private readonly interaction;
    private acknowledged;
    constructor(interaction: CommandInteraction | ButtonInteraction | ModalSubmitInteraction);
    getGuildId(): number;
    getUserId(): number;
    getChannelId(): string;
    isEphemeral(): boolean;
    reply(message: string): Promise<void>;
    replyEmbed(embed: unknown): Promise<void>;
    editEmbed(embed: unknown): Promise<void>;
    deferReply(): Promise<void>;
    getHook(): unknown;
    isAcknowledged(): boolean;
}
