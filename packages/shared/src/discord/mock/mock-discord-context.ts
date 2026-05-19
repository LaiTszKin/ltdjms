import { type DiscordContext } from '../domain/discord-context.js';

/**
 * Mock implementation of DiscordContext for testing.
 * Allows setting and inspecting context values without a real Discord event.
 * Matches Java MockDiscordContext.
 */
export class MockDiscordContext implements DiscordContext {
  private readonly _guildId: number;
  private readonly _userId: number;
  private readonly _channelId: number;
  private readonly _userMention: string;
  private readonly options: Map<string, unknown> = new Map();

  constructor(
    guildId: number,
    userId: number,
    channelId: number,
    userMention: string,
  ) {
    if (guildId <= 0) throw new Error('guildId must be positive');
    if (userId <= 0) throw new Error('userId must be positive');
    if (channelId <= 0) throw new Error('channelId must be positive');
    if (!userMention) throw new Error('userMention must not be empty');

    this._guildId = guildId;
    this._userId = userId;
    this._channelId = channelId;
    this._userMention = userMention;
  }

  getGuildId(): number {
    return this._guildId;
  }

  getUserId(): number {
    return this._userId;
  }

  getChannelId(): number {
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
