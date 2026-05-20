/**
 * Mock implementation of DiscordInteraction for testing.
 * Records all calls for verification.
 * Matches Java MockDiscordInteraction.
 */
export class MockDiscordInteraction {
    _guildId;
    _userId;
    _channelId;
    _ephemeral;
    _acknowledged = false;
    _replyMessages = [];
    _replyEmbeds = [];
    _editedEmbeds = [];
    _deferReplyCount = 0;
    constructor(guildId, userId, channelId = '0', ephemeral = false) {
        this._guildId = guildId;
        this._userId = userId;
        this._channelId = channelId;
        this._ephemeral = ephemeral;
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
    isEphemeral() {
        return this._ephemeral;
    }
    async reply(message) {
        this._replyMessages.push(message);
        this._acknowledged = true;
    }
    async replyEmbed(embed) {
        this._replyEmbeds.push(embed);
        this._acknowledged = true;
    }
    async editEmbed(embed) {
        this._editedEmbeds.push(embed);
    }
    async deferReply() {
        this._deferReplyCount++;
        this._acknowledged = true;
    }
    getHook() {
        return null;
    }
    isAcknowledged() {
        return this._acknowledged;
    }
    // ---- Test helpers ----
    getReplyMessages() {
        return [...this._replyMessages];
    }
    getReplyEmbeds() {
        return [...this._replyEmbeds];
    }
    getEditedEmbeds() {
        return [...this._editedEmbeds];
    }
    getDeferReplyCount() {
        return this._deferReplyCount;
    }
    getReplyCount() {
        return this._replyMessages.length;
    }
    getReplyEmbedCount() {
        return this._replyEmbeds.length;
    }
    getEditEmbedCount() {
        return this._editedEmbeds.length;
    }
    hasReplies() {
        return this._replyMessages.length > 0;
    }
    hasDeferred() {
        return this._deferReplyCount > 0;
    }
    clear() {
        this._replyMessages.length = 0;
        this._replyEmbeds.length = 0;
        this._editedEmbeds.length = 0;
        this._deferReplyCount = 0;
        this._acknowledged = false;
    }
}
//# sourceMappingURL=mock-discord-interaction.js.map