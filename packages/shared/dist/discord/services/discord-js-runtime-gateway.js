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
export class DiscordJsRuntimeGateway {
    clientRef = null;
    isReady() {
        return this.clientRef !== null;
    }
    publishReady(client) {
        if (this.clientRef !== null) {
            throw new Error('Discord runtime has already been published');
        }
        this.clientRef = client;
    }
    requireReadyClient() {
        if (!this.clientRef) {
            throw new DiscordRuntimeNotReadyError();
        }
        return this.clientRef;
    }
    findGuild(guildId) {
        const client = this.requireReadyClient();
        return client.guilds.cache.get(guildId) ?? null;
    }
    findGuildChannel(guildId, channelId) {
        const client = this.requireReadyClient();
        const guild = client.guilds.cache.get(guildId);
        if (!guild)
            return null;
        return guild.channels.cache.get(channelId) ?? null;
    }
    selfUserId() {
        const client = this.requireReadyClient();
        return client.user?.id ?? '';
    }
}
//# sourceMappingURL=discord-js-runtime-gateway.js.map