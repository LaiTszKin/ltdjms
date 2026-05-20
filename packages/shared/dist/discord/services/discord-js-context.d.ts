import { type CommandInteraction, type ButtonInteraction, type ModalSubmitInteraction } from 'discord.js';
import { type DiscordContext } from '../domain/discord-context.js';
/**
 * Discord.js implementation of DiscordContext.
 * Wraps a discord.js interaction to provide guild/user/channel context.
 */
export declare class DiscordJsContext implements DiscordContext {
    private readonly interaction;
    constructor(interaction: CommandInteraction | ButtonInteraction | ModalSubmitInteraction);
    getGuildId(): number;
    getUserId(): number;
    getChannelId(): number;
    getUserMention(): string;
    getOption(name: string): string | null;
    getOptionAsString(name: string): string | null;
    getOptionAsNumber(name: string): number | null;
}
