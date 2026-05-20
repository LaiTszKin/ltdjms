import { SlashCommandMetrics } from './SlashCommandMetrics.js';
import { BotErrorHandler } from './BotErrorHandler.js';
/**
 * Centralized slash command and interaction dispatcher.
 * Listens to discord.js interactionCreate events via a registered callback.
 * Matches Java SlashCommandListener.
 */
export class SlashCommandListener {
    commands = new Map();
    interactionHandlers = new Map();
    metrics;
    errorHandler;
    constructor(metrics, errorHandler) {
        this.metrics = metrics ?? new SlashCommandMetrics();
        this.errorHandler = errorHandler ?? new BotErrorHandler();
    }
    // ---- Registration ----
    /**
     * Registers a slash command handler.
     */
    registerCommand(handler) {
        this.commands.set(handler.commandName, handler);
    }
    /**
     * Registers an interaction handler (buttons, select menus, modals).
     */
    registerInteractionHandler(handler) {
        this.interactionHandlers.set(handler.customIdPrefix, handler);
    }
    /**
     * Registers multiple commands at once.
     */
    registerCommands(handlers) {
        for (const h of handlers) {
            this.registerCommand(h);
        }
    }
    /**
     * Registers multiple interaction handlers at once.
     */
    registerInteractionHandlers(handlers) {
        for (const h of handlers) {
            this.registerInteractionHandler(h);
        }
    }
    // ---- Dispatch ----
    /**
     * Routes an interaction to the appropriate handler.
     * Determines the interaction type and dispatches accordingly.
     */
    async onInteraction(interaction, context, type, commandNameOrCustomId) {
        const startTime = this.metrics.recordStart(commandNameOrCustomId);
        try {
            // Defer reply if not yet acknowledged
            if (!interaction.isAcknowledged()) {
                await interaction.deferReply();
            }
            let success = false;
            switch (type) {
                case 'chatInput':
                    success = await this.dispatchCommand(commandNameOrCustomId, interaction, context);
                    break;
                case 'button':
                case 'stringSelect':
                case 'modalSubmit':
                    success = await this.dispatchInteraction(commandNameOrCustomId, interaction, context);
                    break;
            }
            const elapsedMs = performance.now() - startTime;
            this.metrics.recordEnd(commandNameOrCustomId, elapsedMs, success);
        }
        catch (err) {
            const elapsedMs = performance.now() - startTime;
            this.metrics.recordEnd(commandNameOrCustomId, elapsedMs, false);
            await this.errorHandler.handle(err, interaction);
        }
    }
    /** Returns the metrics collector instance. */
    getMetrics() {
        return this.metrics;
    }
    /** Returns the error handler instance. */
    getErrorHandler() {
        return this.errorHandler;
    }
    // ---- Private ----
    async dispatchCommand(commandName, interaction, context) {
        const handler = this.commands.get(commandName);
        if (!handler) {
            await interaction.reply(`未知的指令：${commandName}`);
            return false;
        }
        await handler.execute(interaction, context);
        return true;
    }
    async dispatchInteraction(customId, interaction, context) {
        // Prefix matching: find the handler with the longest matching prefix
        let matchedHandler = null;
        let longestPrefix = '';
        for (const [prefix, handler] of this.interactionHandlers) {
            if (customId.startsWith(prefix) && prefix.length > longestPrefix.length) {
                matchedHandler = handler;
                longestPrefix = prefix;
            }
        }
        if (!matchedHandler) {
            await interaction.reply('未知的操作');
            return false;
        }
        await matchedHandler.execute(interaction, context);
        return true;
    }
}
//# sourceMappingURL=SlashCommandListener.js.map