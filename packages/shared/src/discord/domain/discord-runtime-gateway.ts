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

  /** Finds a thread channel by guild and thread ID. */
  findThreadChannel(guildId: string, threadId: string): unknown | null;

  /** Sends a direct message to a user. Returns true if the message was sent. */
  sendDM(userId: string, message: Record<string, unknown>): Promise<boolean>;

  /** Checks if a guild member is currently online. Returns false on any error. */
  isMemberOnline(guildId: string, userId: string): Promise<boolean>;

  /**
   * Checks if a user is a member of the specified guild.
   * Returns true if the member exists in the guild, false otherwise.
   * Returns false on any error (e.g., guild not found, user not found).
   */
  retrieveMemberById(guildId: string, userId: string): Promise<boolean>;
}
