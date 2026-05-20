import { CommandInteraction, type ButtonInteraction, type ModalSubmitInteraction } from 'discord.js';
import { type DiscordInteraction } from '../domain/discord-interaction.js';
/**
 * Discord.js implementation of DiscordInteraction.
 * Wraps CommandInteraction / ButtonInteraction / ModalSubmitInteraction.
 */
export declare class DiscordJsInteraction implements DiscordInteraction {
    private readonly interaction;
    private acknowledged;
    private _ephemeral;
    constructor(interaction: CommandInteraction | ButtonInteraction | ModalSubmitInteraction, ephemeral?: boolean);
    getGuildId(): string;
    getUserId(): string;
    isEphemeral(): boolean;
    reply(message: string): Promise<void>;
    replyEmbed(embed: unknown): Promise<void>;
    editEmbed(embed: unknown): Promise<void>;
    deferReply(): Promise<void>;
    getHook(): unknown;
    isAcknowledged(): boolean;
}
