/**
 * Mock implementation of DiscordContext for testing.
 * Allows setting and inspecting context values without a real Discord event.
 * Matches Java MockDiscordContext.
 */
export class MockDiscordContext {
    _guildId;
    _userId;
    _channelId;
    _userMention;
    options = new Map();
    constructor(guildId, userId, channelId, userMention) {
        if (!guildId)
            throw new Error('guildId must be a valid non-empty id');
        if (!userId || userId === '0')
            throw new Error('userId must be a valid non-zero id');
        if (!channelId || channelId === '0')
            throw new Error('channelId must be a valid non-zero id');
        if (!userMention)
            throw new Error('userMention must not be empty');
        this._guildId = guildId;
        this._userId = userId;
        this._channelId = channelId;
        this._userMention = userMention;
    }
    getGuildId() {
        return this._guildId;
    }
    getUserId() {
        return this._userId;
    }
    getChannelId() {
        return this._channelId;
    }
    getUserMention() {
        return this._userMention;
    }
    getOption(name) {
        const value = this.options.get(name);
        return value?.toString() ?? null;
    }
    getOptionAsString(name) {
        const value = this.options.get(name);
        return typeof value === 'string' ? value : null;
    }
    getOptionAsNumber(name) {
        const value = this.options.get(name);
        if (typeof value === 'number')
            return value;
        if (typeof value === 'string') {
            const n = Number(value);
            return Number.isNaN(n) ? null : n;
        }
        return null;
    }
    // ---- Test helpers ----
    setOption(name, value) {
        this.options.set(name, value);
    }
    clearOption(name) {
        this.options.delete(name);
    }
    clearAllOptions() {
        this.options.clear();
    }
    hasOption(name) {
        return this.options.has(name);
    }
    getOptionCount() {
        return this.options.size;
    }
}
//# sourceMappingURL=mock-discord-context.js.map