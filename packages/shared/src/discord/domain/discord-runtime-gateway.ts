/**
 * Discord runtime access boundary for dependency injection.
 * Exposes minimal runtime capabilities without direct JDA/discord.js dependency.
 * Matches Java DiscordRuntimeGateway interface.
 */
export interface DiscordRuntimeGateway {
  /** Returns whether the runtime has been published by bootstrap. */
  isReady(): boolean;

  /** Publishes the ready discord.js Client instance. Can only be called once. */
  publishReady(client: unknown): void;

  /** Returns the ready client, throwing if not yet published. */
  requireReadyClient(): unknown;

  /** Finds a guild by ID. */
  findGuild(guildId: string): unknown | null;

  /** Finds a guild channel by guild and channel ID. */
  findGuildChannel(guildId: string, channelId: string): unknown | null;

  /** Gets the bot's self user ID. */
  selfUserId(): string;
}
