import { describe, it, expect, beforeEach } from 'vitest';
import { DomainError, DomainErrorCategory, MockDiscordInteraction, } from '@ltdjms/shared';
import { BotErrorHandler } from '../BotErrorHandler.js';
describe('BotErrorHandler', () => {
    let handler;
    let mockInteraction;
    beforeEach(() => {
        handler = new BotErrorHandler();
        mockInteraction = new MockDiscordInteraction('1', '100');
    });
    describe('DomainError handling', () => {
        it('should map all DomainError categories to zh-TW messages', () => {
            const categories = Object.values(DomainErrorCategory);
            for (const category of categories) {
                const error = new DomainError(category, `Test ${category}`);
                const message = handler.toUserMessage(error);
                expect(message).toBeTruthy();
                expect(message.length).toBeGreaterThan(0);
            }
        });
        it('should include error details when available', () => {
            const error = DomainError.insufficientBalance('Current balance: 100');
            const message = handler.toUserMessage(error);
            expect(message).toContain('100');
        });
        it('should handle INSUFFICIENT_BALANCE', () => {
            const error = DomainError.insufficientBalance('Not enough coins');
            const message = handler.toUserMessage(error);
            expect(message).toContain('不足');
        });
        it('should handle INSUFFICIENT_PERMISSIONS', () => {
            const error = DomainError.insufficientPermissions('Admin only');
            const message = handler.toUserMessage(error);
            expect(message).toContain('權限');
        });
        it('should handle DUPLICATE_CHANNEL', () => {
            const error = DomainError.duplicateChannel('Already in list');
            const message = handler.toUserMessage(error);
            const hasDuplicateOrExists = message.includes('重複') || message.includes('已在');
            expect(hasDuplicateOrExists).toBe(true);
        });
    });
    describe('unexpected errors', () => {
        it('should return generic message for unknown errors', () => {
            const message = handler.toUserMessage(new Error('Something broke'));
            expect(message).toBeTruthy();
        });
        it('should handle non-Error types', () => {
            const message = handler.toUserMessage('string error');
            expect(message).toBeTruthy();
        });
        it('should handle null/undefined', () => {
            const message = handler.toUserMessage(null);
            expect(message).toBeTruthy();
        });
    });
    describe('Discord API errors', () => {
        it('should map code 10062', () => {
            const message = handler.toUserMessage({ code: 10062, message: 'Unknown interaction' });
            expect(message).toBeTruthy();
        });
        it('should map code 50001', () => {
            const message = handler.toUserMessage({ code: 50001, message: 'Missing Access' });
            expect(message).toContain('權限');
        });
    });
    describe('handle method', () => {
        it('should reply when not acknowledged', async () => {
            const error = DomainError.insufficientBalance('Not enough');
            await handler.handle(error, mockInteraction);
            expect(mockInteraction.hasReplies()).toBe(true);
        });
    });
});
//# sourceMappingURL=BotErrorHandler.test.js.map