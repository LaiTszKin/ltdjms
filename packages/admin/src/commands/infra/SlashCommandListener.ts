import {
  type DiscordInteraction,
  type DiscordContext,
  type DiscordRuntimeGateway,
  DiscordJsInteraction,
  DiscordJsContext,
} from '@ltdjms/shared';
import { type Client } from 'discord.js';
import { type CommandHandler, type InteractionHandler } from '@ltdjms/shared';
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
  private listening = false;

  constructor(
    metrics?: SlashCommandMetrics,
    errorHandler?: BotErrorHandler,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    private readonly logger?: any,
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

  // ---- Client wiring ----

  /**
   * Wires this listener to the Discord client's interactionCreate event.
   * Should be called once during DI setup after all handlers are registered.
   */
  listen(gateway: DiscordRuntimeGateway): void {
    if (this.listening) return;
    this.listening = true;

    const client = gateway.requireReadyClient() as Client;
    client.on('interactionCreate', (rawInteraction) => {
      void (async () => {
        this.logger?.info({ type: 'interactionCreate' }, 'Interaction received');
        try {
          await this.handleRawInteraction(rawInteraction);
        } catch (err) {
          this.logger?.error(
            { err },
            '[SlashCommandListener] Unhandled error in interactionCreate',
          );
          console.error('[SlashCommandListener] Unhandled error in interactionCreate:', err);
        }
      })();
    });
  }

  private async handleRawInteraction(rawInteraction: unknown): Promise<void> {
    // Determine interaction type and extract name/customId
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const interaction = rawInteraction as any;

    let type: InteractionType;
    let commandNameOrCustomId: string;

    if (typeof interaction.isChatInputCommand === 'function' && interaction.isChatInputCommand()) {
      type = 'chatInput';
      commandNameOrCustomId = String(interaction.commandName ?? '');
    } else if (typeof interaction.isButton === 'function' && interaction.isButton()) {
      type = 'button';
      commandNameOrCustomId = String(interaction.customId ?? '');
    } else if (typeof interaction.isAnySelectMenu === 'function' && interaction.isAnySelectMenu()) {
      type = 'stringSelect';
      commandNameOrCustomId = String(interaction.customId ?? '');
    } else if (typeof interaction.isModalSubmit === 'function' && interaction.isModalSubmit()) {
      type = 'modalSubmit';
      commandNameOrCustomId = String(interaction.customId ?? '');
    } else {
      // Unknown interaction type — ignore
      return;
    }

    const discordInteraction = this.createInteraction(rawInteraction);
    const discordContext = this.createContext(rawInteraction);

    await this.onInteraction(discordInteraction, discordContext, type, commandNameOrCustomId);
  }

  private createInteraction(raw: unknown): DiscordInteraction {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return new DiscordJsInteraction(raw as any);
  }

  private createContext(raw: unknown): DiscordContext {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return new DiscordJsContext(raw as any);
  }

  // ---- Dispatch ----

  /**
   * Routes an interaction to the appropriate handler.
   * Determines the interaction type and dispatches accordingly.
   * NOTE: Handlers manage their own reply lifecycle (defer, modal, etc.).
   */
  async onInteraction(
    interaction: DiscordInteraction,
    context: DiscordContext,
    type: InteractionType,
    commandNameOrCustomId: string,
  ): Promise<void> {
    // Handlers manage their own reply lifecycle (defer, modal, etc.).
    // No blanket auto-defer — chatInput commands need modal support,
    // and button/modal handlers manage their own acknowledgement strategy.

    const startTime = this.metrics.recordStart(commandNameOrCustomId);

    try {
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
      this.logger?.info({ commandNameOrCustomId, type, elapsedMs, success }, 'Interaction handled');
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
