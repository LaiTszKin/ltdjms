import { type Client } from 'discord.js';
import { type DiscordRuntimeGateway } from '../domain/discord-runtime-gateway.js';

/**
 * Thrown when Discord runtime access is requested before publishReady.
 * Matches Java DiscordRuntimeNotReadyException.
 */
export class DiscordRuntimeNotReadyError extends Error {
  constructor(message = 'Discord runtime is not ready yet') {
    super(message);
    this.name = 'DiscordRuntimeNotReadyError';
  }
}

/**
 * Discord.js implementation of DiscordRuntimeGateway.
 * Uses AtomicRef<Client> pattern — bootstrap calls publishReady after client login.
 */
export class DiscordJsRuntimeGateway implements DiscordRuntimeGateway {
  private clientRef: Client | null = null;

  isReady(): boolean {
    return this.clientRef !== null;
  }

  publishReady(client: unknown): void {
    if (this.clientRef !== null) {
      throw new Error('Discord runtime has already been published');
    }
    this.clientRef = client as Client;
  }

  requireReadyClient(): unknown {
    if (!this.clientRef) {
      throw new DiscordRuntimeNotReadyError();
    }
    return this.clientRef;
  }

  findGuild(guildId: string): unknown | null {
    const client = this.requireReadyClient() as Client;
    return client.guilds.cache.get(guildId) ?? null;
  }

  findGuildChannel(guildId: string, channelId: string): unknown | null {
    const client = this.requireReadyClient() as Client;
    const guild = client.guilds.cache.get(guildId);
    if (!guild) return null;
    return guild.channels.cache.get(channelId) ?? null;
  }

  selfUserId(): string {
    const client = this.requireReadyClient() as Client;
    return client.user?.id ?? '';
  }

  findThreadChannel(guildId: string, threadId: string): unknown | null {
    const client = this.requireReadyClient() as Client;
    const guild = client.guilds.cache.get(guildId);
    if (!guild) return null;
    return guild.channels.cache.get(threadId) ?? null;
  }
}
