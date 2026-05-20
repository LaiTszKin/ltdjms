import { describe, it, expect } from 'vitest';
import { MockDiscordInteraction } from '../mock-discord-interaction.js';
describe('MockDiscordInteraction', () => {
    it('records guild ID and user ID', () => {
        const mock = new MockDiscordInteraction('123', '456');
        expect(mock.getGuildId()).toBe('123');
        expect(mock.getUserId()).toBe('456');
    });
    it('starts unacknowledged', () => {
        const mock = new MockDiscordInteraction('123', '456');
        expect(mock.isAcknowledged()).toBe(false);
    });
    it('records reply messages', async () => {
        const mock = new MockDiscordInteraction('123', '456');
        await mock.reply('Hello');
        expect(mock.isAcknowledged()).toBe(true);
        expect(mock.getReplyMessages()).toEqual(['Hello']);
        expect(mock.hasReplies()).toBe(true);
        expect(mock.getReplyCount()).toBe(1);
    });
    it('records reply embeds', async () => {
        const mock = new MockDiscordInteraction('123', '456');
        await mock.replyEmbed({ title: 'Test Embed' });
        expect(mock.isAcknowledged()).toBe(true);
        expect(mock.getReplyEmbeds()).toHaveLength(1);
        expect(mock.getReplyEmbedCount()).toBe(1);
        expect(mock.hasReplies()).toBe(false);
    });
    it('records edit embeds', async () => {
        const mock = new MockDiscordInteraction('123', '456');
        await mock.editEmbed({ title: 'Edited' });
        expect(mock.getEditedEmbeds()).toHaveLength(1);
        expect(mock.getEditEmbedCount()).toBe(1);
    });
    it('records defer reply', async () => {
        const mock = new MockDiscordInteraction('123', '456');
        expect(mock.hasDeferred()).toBe(false);
        await mock.deferReply();
        expect(mock.isAcknowledged()).toBe(true);
        expect(mock.getDeferReplyCount()).toBe(1);
        expect(mock.hasDeferred()).toBe(true);
    });
    it('supports ephemeral mode', () => {
        const mock = new MockDiscordInteraction('123', '456', '0', true);
        expect(mock.isEphemeral()).toBe(true);
    });
    it('clears recorded data', async () => {
        const mock = new MockDiscordInteraction('123', '456');
        await mock.reply('Test');
        await mock.deferReply();
        expect(mock.hasReplies()).toBe(true);
        expect(mock.hasDeferred()).toBe(true);
        mock.clear();
        expect(mock.hasReplies()).toBe(false);
        expect(mock.hasDeferred()).toBe(false);
        expect(mock.isAcknowledged()).toBe(false);
    });
    it('getHook returns null', () => {
        const mock = new MockDiscordInteraction('123', '456');
        expect(mock.getHook()).toBeNull();
    });
});
//# sourceMappingURL=mock-interaction.test.js.map