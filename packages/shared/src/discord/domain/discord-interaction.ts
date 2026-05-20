/**
 * Unified abstraction for Discord interaction replies.
 * Matches Java DiscordInteraction interface.
 */
export interface DiscordInteraction {
  /** Gets the guild ID. */
  getGuildId(): string;

  /** Gets the user ID. */
  getUserId(): string;

  /** Checks if this interaction is ephemeral (user-only visible). */
  isEphemeral(): boolean;

  /** Replies with a plain text message. */
  reply(message: string): Promise<void>;

  /** Replies with an embed message. */
  replyEmbed(embed: unknown): Promise<void>;

  /** Edits the original reply's embed. */
  editEmbed(embed: unknown): Promise<void>;

  /** Defers the reply (tells Discord to expect a later response). */
  deferReply(): Promise<void>;

  /** Returns the underlying interaction hook. */
  getHook(): unknown;

  /** Gets the full customId of the interaction. */
  getCustomId(): string;

  /** Checks if the interaction has been acknowledged. */
  isAcknowledged(): boolean;

  /** Checks if the user has ADMINISTRATOR permission or is guild owner. */
  isAdministrator(): boolean;

  /** Gets the channel ID. */
  getChannelId(): string;

  /** Checks if the user has a specific permission. */
  hasPermission(permission: bigint): boolean;
}
