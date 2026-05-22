import { type DiscordContext } from '../domain/discord-context.js';

/**
 * Mock implementation of DiscordContext for testing.
 * Allows setting and inspecting context values without a real Discord event.
 * Matches Java MockDiscordContext.
 */
export class MockDiscordContext implements DiscordContext {
  private readonly _guildId: string;
  private readonly _userId: string;
  private readonly _channelId: string;
  private readonly _userMention: string;
  private readonly options: Map<string, unknown> = new Map();

  constructor(guildId: string, userId: string, channelId: string, userMention: string) {
    if (!guildId) throw new Error('guildId must be a valid non-empty id');
    if (!userId || userId === '0') throw new Error('userId must be a valid non-zero id');
    if (!channelId || channelId === '0') throw new Error('channelId must be a valid non-zero id');
    if (!userMention) throw new Error('userMention must not be empty');

    this._guildId = guildId;
    this._userId = userId;
    this._channelId = channelId;
    this._userMention = userMention;
  }

  getGuildId(): string {
    return this._guildId;
  }

  getUserId(): string {
    return this._userId;
  }

  getChannelId(): string {
    return this._channelId;
  }

  getUserMention(): string {
    return this._userMention;
  }

  getOption(name: string): string | null {
    const value = this.options.get(name);
    return value?.toString() ?? null;
  }

  getOptionAsString(name: string): string | null {
    const value = this.options.get(name);
    return typeof value === 'string' ? value : null;
  }

  getOptionAsNumber(name: string): number | null {
    const value = this.options.get(name);
    if (typeof value === 'number') return value;
    if (typeof value === 'string') {
      const n = Number(value);
      return Number.isNaN(n) ? null : n;
    }
    return null;
  }

  getOptionAsUser(name: string): unknown | null {
    const value = this.options.get(name);
    return value ?? null;
  }

  // ---- Test helpers ----

  setOption(name: string, value: unknown): void {
    this.options.set(name, value);
  }

  clearOption(name: string): void {
    this.options.delete(name);
  }

  clearAllOptions(): void {
    this.options.clear();
  }

  hasOption(name: string): boolean {
    return this.options.has(name);
  }

  getOptionCount(): number {
    return this.options.size;
  }
}
