/**
 * Unified abstraction for extracting context from Discord events.
 * Matches Java DiscordContext interface.
 */
export interface DiscordContext {
  /** Gets the guild ID. */
  getGuildId(): string;

  /** Gets the user ID. */
  getUserId(): string;

  /** Gets the channel ID. */
  getChannelId(): string;

  /** Gets the user mention string (e.g., "<@123456789>"). */
  getUserMention(): string;

  /** Gets a command option's raw string value. */
  getOption(name: string): string | null;

  /** Gets a command option as a string. */
  getOptionAsString(name: string): string | null;

  /** Gets a command option as a number. */
  getOptionAsNumber(name: string): number | null;
}
