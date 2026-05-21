import { type DiscordInteraction } from '../domain/discord-interaction.js';

/**
 * Mock implementation of DiscordInteraction for testing.
 * Records all calls for verification.
 * Matches Java MockDiscordInteraction.
 */
export class MockDiscordInteraction implements DiscordInteraction {
  private readonly _guildId: string;
  private readonly _userId: string;
  private readonly _channelId: string | undefined;
  private readonly _ephemeral: boolean;
  private readonly _customId: string;
  private _isAdministrator: boolean;
  private readonly _interactionType: 'button' | 'modalSubmit' | 'chatInput' | 'stringSelect';
  private _acknowledged = false;

  private readonly _replyMessages: string[] = [];
  private readonly _replyEmbeds: unknown[] = [];
  private readonly _editedEmbeds: unknown[] = [];
  private _deferReplyCount = 0;

  constructor(
    guildId: string,
    userId: string,
    channelId?: string,
    ephemeral = false,
    customId = '',
    isAdmin = false,
    interactionType: 'button' | 'modalSubmit' | 'chatInput' | 'stringSelect' = 'button',
  ) {
    this._guildId = guildId;
    this._userId = userId;
    this._channelId = channelId;
    this._ephemeral = ephemeral;
    this._customId = customId;
    this._isAdministrator = isAdmin;
    this._interactionType = interactionType;
  }

  getChannelId(): string {
    return this._channelId ?? '0';
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

  getCustomId(): string {
    return this._customId;
  }

  isAcknowledged(): boolean {
    return this._acknowledged;
  }

  isAdministrator(): boolean {
    return this._isAdministrator;
  }

  hasPermission(_permission: bigint): boolean {
    return this._isAdministrator;
  }

  async showModal(_modal: unknown): Promise<void> {
    // No-op in mock
  }

  getSelectedValues(): string[] {
    return [];
  }

  getTextInputValue(_customId: string): string {
    return '';
  }

  getGuildName(): string | null {
    return null;
  }

  getChannelName(_channelId: string): string | null {
    return null;
  }

  isButton(): boolean {
    return this._interactionType === 'button';
  }

  isModalSubmit(): boolean {
    return this._interactionType === 'modalSubmit';
  }

  async replyWithComponents(
    _embed: unknown,
    _components: unknown[],
  ): Promise<{ channelId: string; id: string } | null> {
    this._acknowledged = true;
    return null;
  }

  async editWithComponents(_embed: unknown, _components: unknown[]): Promise<void> {
    // No-op in mock
  }

  /** Sets the admin flag for testing. */
  setAdministrator(isAdmin: boolean): void {
    this._isAdministrator = isAdmin;
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
