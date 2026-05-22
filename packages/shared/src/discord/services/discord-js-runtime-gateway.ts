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
    // Thread channels may be in threads.cache (especially archived threads)
    return (
      guild.channels.cache.get(threadId) ??
      (guild as { threads?: { cache: Map<string, unknown> } }).threads?.cache.get(threadId) ??
      null
    );
  }

  async sendDM(userId: string, message: Record<string, unknown>): Promise<boolean> {
    const client = this.requireReadyClient() as Client;
    try {
      const user = await client.users.fetch(userId);
      if (!user) return false;
      await user.send(message);
      return true;
    } catch {
      return false;
    }
  }

  async isMemberOnline(guildId: string, userId: string): Promise<boolean> {
    const client = this.requireReadyClient() as Client;
    try {
      const guild = client.guilds.cache.get(guildId);
      if (!guild) return false;
      const member = await guild.members.fetch(userId);
      return member.presence?.status === 'online';
    } catch {
      return false;
    }
  }

  async retrieveMemberById(guildId: string, userId: string): Promise<boolean> {
    const client = this.requireReadyClient() as Client;
    try {
      const guild = client.guilds.cache.get(guildId);
      if (!guild) return false;
      await guild.members.fetch(userId);
      return true;
    } catch {
      return false;
    }
  }
}
