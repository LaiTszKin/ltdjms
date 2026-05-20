import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type CommandHandler, type InteractionHandler } from './CommandHandler.js';
import { SlashCommandMetrics } from './SlashCommandMetrics.js';
import { BotErrorHandler } from './BotErrorHandler.js';
/**
 * Types of interactions that can be handled.
 */
type InteractionType = 'chatInput' | 'button' | 'stringSelect' | 'modalSubmit';
/**
 * Centralized slash command and interaction dispatcher.
 * Listens to discord.js interactionCreate events via a registered callback.
 * Matches Java SlashCommandListener.
 */
export declare class SlashCommandListener {
    private readonly commands;
    private readonly interactionHandlers;
    private readonly metrics;
    private readonly errorHandler;
    constructor(metrics?: SlashCommandMetrics, errorHandler?: BotErrorHandler);
    /**
     * Registers a slash command handler.
     */
    registerCommand(handler: CommandHandler): void;
    /**
     * Registers an interaction handler (buttons, select menus, modals).
     */
    registerInteractionHandler(handler: InteractionHandler): void;
    /**
     * Registers multiple commands at once.
     */
    registerCommands(handlers: CommandHandler[]): void;
    /**
     * Registers multiple interaction handlers at once.
     */
    registerInteractionHandlers(handlers: InteractionHandler[]): void;
    /**
     * Routes an interaction to the appropriate handler.
     * Determines the interaction type and dispatches accordingly.
     */
    onInteraction(interaction: DiscordInteraction, context: DiscordContext, type: InteractionType, commandNameOrCustomId: string): Promise<void>;
    /** Returns the metrics collector instance. */
    getMetrics(): SlashCommandMetrics;
    /** Returns the error handler instance. */
    getErrorHandler(): BotErrorHandler;
    private dispatchCommand;
    private dispatchInteraction;
}
export {};
