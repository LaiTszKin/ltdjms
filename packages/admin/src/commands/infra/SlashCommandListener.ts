import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
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
export class SlashCommandListener {
  private readonly commands = new Map<string, CommandHandler>();
  private readonly interactionHandlers = new Map<string, InteractionHandler>();
  private readonly metrics: SlashCommandMetrics;
  private readonly errorHandler: BotErrorHandler;

  constructor(
    metrics?: SlashCommandMetrics,
    errorHandler?: BotErrorHandler,
  ) {
    this.metrics = metrics ?? new SlashCommandMetrics();
    this.errorHandler = errorHandler ?? new BotErrorHandler();
  }

  // ---- Registration ----

  /**
   * Registers a slash command handler.
   */
  registerCommand(handler: CommandHandler): void {
    this.commands.set(handler.commandName, handler);
  }

  /**
   * Registers an interaction handler (buttons, select menus, modals).
   */
  registerInteractionHandler(handler: InteractionHandler): void {
    this.interactionHandlers.set(handler.customIdPrefix, handler);
  }

  /**
   * Registers multiple commands at once.
   */
  registerCommands(handlers: CommandHandler[]): void {
    for (const h of handlers) {
      this.registerCommand(h);
    }
  }

  /**
   * Registers multiple interaction handlers at once.
   */
  registerInteractionHandlers(handlers: InteractionHandler[]): void {
    for (const h of handlers) {
      this.registerInteractionHandler(h);
    }
  }

  // ---- Dispatch ----

  /**
   * Routes an interaction to the appropriate handler.
   * Determines the interaction type and dispatches accordingly.
   */
  async onInteraction(
    interaction: DiscordInteraction,
    context: DiscordContext,
    type: InteractionType,
    commandNameOrCustomId: string,
  ): Promise<void> {
    const startTime = this.metrics.recordStart(commandNameOrCustomId);

    try {
      // Defer reply if not yet acknowledged
      if (!interaction.isAcknowledged()) {
        await interaction.deferReply();
      }

      let success = false;

      switch (type) {
        case 'chatInput':
          success = await this.dispatchCommand(
            commandNameOrCustomId,
            interaction,
            context,
          );
          break;
        case 'button':
        case 'stringSelect':
        case 'modalSubmit':
          success = await this.dispatchInteraction(
            commandNameOrCustomId,
            interaction,
            context,
          );
          break;
      }

      const elapsedMs = performance.now() - startTime;
      this.metrics.recordEnd(commandNameOrCustomId, elapsedMs, success);
    } catch (err) {
      const elapsedMs = performance.now() - startTime;
      this.metrics.recordEnd(commandNameOrCustomId, elapsedMs, false);

      await this.errorHandler.handle(err, interaction);
    }
  }

  /** Returns the metrics collector instance. */
  getMetrics(): SlashCommandMetrics {
    return this.metrics;
  }

  /** Returns the error handler instance. */
  getErrorHandler(): BotErrorHandler {
    return this.errorHandler;
  }

  // ---- Private ----

  private async dispatchCommand(
    commandName: string,
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<boolean> {
    const handler = this.commands.get(commandName);
    if (!handler) {
      await interaction.reply(`未知的指令：${commandName}`);
      return false;
    }

    await handler.execute(interaction, context);
    return true;
  }

  private async dispatchInteraction(
    customId: string,
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<boolean> {
    // Prefix matching: find the handler with the longest matching prefix
    let matchedHandler: InteractionHandler | null = null;
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
