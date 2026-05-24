/**
 * User panel session data stored in the session.
 */
export interface PanelSessionData {
  guildId: string;
  userId: string;
  createdAt: number;
  lastAccessedAt: number;
  /** The channel ID where the user panel was last rendered. Used by listeners for push updates. */
  channelId?: string;
  /** The message ID of the last panel embed. Used by listeners for push updates. */
  messageId?: string;
  /** Arbitrary context key-value pairs for tracking session-specific state (e.g. pagination). */
  context?: Record<string, string>;
}
