import { type DiscordInteraction } from '../domain/discord-interaction.js';

/**
 * Mock implementation of DiscordInteraction for testing.
 * Records all calls for verification.
 * Matches Java MockDiscordInteraction.
 */
export class MockDiscordInteraction implements DiscordInteraction {
  private readonly _guildId: string;
  private readonly _userId: string;
  private readonly _ephemeral: boolean;
  private _acknowledged = false;

  private readonly _replyMessages: string[] = [];
  private readonly _replyEmbeds: unknown[] = [];
  private readonly _editedEmbeds: unknown[] = [];
  private _deferReplyCount = 0;

  constructor(
    guildId: string,
    userId: string,
    _channelId?: string,
    ephemeral = false,
  ) {
    this._guildId = guildId;
    this._userId = userId;
    this._ephemeral = ephemeral;
  }

  getGuildId(): string {
    return this._guildId;
  }

  getUserId(): string {
    return this._userId;
  }

  isEphemeral(): boolean {
    return this._ephemeral;
  }

  async reply(message: string): Promise<void> {
    this._replyMessages.push(message);
    this._acknowledged = true;
  }

  async replyEmbed(embed: unknown): Promise<void> {
    this._replyEmbeds.push(embed);
    this._acknowledged = true;
  }

  async editEmbed(embed: unknown): Promise<void> {
    this._editedEmbeds.push(embed);
  }

  async deferReply(): Promise<void> {
    this._deferReplyCount++;
    this._acknowledged = true;
  }

  getHook(): unknown {
    return null;
  }

  isAcknowledged(): boolean {
    return this._acknowledged;
  }

  // ---- Test helpers ----

  getReplyMessages(): string[] {
    return [...this._replyMessages];
  }

  getReplyEmbeds(): unknown[] {
    return [...this._replyEmbeds];
  }

  getEditedEmbeds(): unknown[] {
    return [...this._editedEmbeds];
  }

  getDeferReplyCount(): number {
    return this._deferReplyCount;
  }

  getReplyCount(): number {
    return this._replyMessages.length;
  }

  getReplyEmbedCount(): number {
    return this._replyEmbeds.length;
  }

  getEditEmbedCount(): number {
    return this._editedEmbeds.length;
  }

  hasReplies(): boolean {
    return this._replyMessages.length > 0;
  }

  hasDeferred(): boolean {
    return this._deferReplyCount > 0;
  }

  clear(): void {
    this._replyMessages.length = 0;
    this._replyEmbeds.length = 0;
    this._editedEmbeds.length = 0;
    this._deferReplyCount = 0;
    this._acknowledged = false;
  }
}
