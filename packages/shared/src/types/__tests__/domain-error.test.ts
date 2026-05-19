import { describe, it, expect } from 'vitest';
import { DomainError, DomainErrorCategory } from '../domain-error.js';

describe('DomainError', () => {
  describe('constructor', () => {
    it('creates a domain error with category and message', () => {
      const error = new DomainError(
        DomainErrorCategory.INVALID_INPUT,
        'Invalid input provided',
      );
      expect(error.category).toBe(DomainErrorCategory.INVALID_INPUT);
      expect(error.message).toBe('Invalid input provided');
      expect(error.cause).toBeUndefined();
    });

    it('rejects null category', () => {
      expect(
        () =>
          new DomainError(
            null as unknown as DomainErrorCategory,
            'message',
          ),
      ).toThrow('category must not be null');
    });
  });

  describe('static factory methods', () => {
    it('invalidInput', () => {
      const e = DomainError.invalidInput('bad input');
      expect(e.category).toBe(DomainErrorCategory.INVALID_INPUT);
      expect(e.message).toBe('bad input');
    });

    it('insufficientBalance', () => {
      const e = DomainError.insufficientBalance('not enough funds');
      expect(e.category).toBe(DomainErrorCategory.INSUFFICIENT_BALANCE);
    });

    it('insufficientTokens', () => {
      const e = DomainError.insufficientTokens('not enough tokens');
      expect(e.category).toBe(DomainErrorCategory.INSUFFICIENT_TOKENS);
    });

    it('persistenceFailure', () => {
      const cause = new Error('DB down');
      const e = DomainError.persistenceFailure('DB failed', cause);
      expect(e.category).toBe(DomainErrorCategory.PERSISTENCE_FAILURE);
      expect(e.cause).toBe(cause);
    });

    it('unexpectedFailure', () => {
      const cause = new Error('bug');
      const e = DomainError.unexpectedFailure('unexpected', cause);
      expect(e.category).toBe(DomainErrorCategory.UNEXPECTED_FAILURE);
      expect(e.cause).toBe(cause);
    });

    it('discordInteractionTimeout', () => {
      const e = DomainError.discordInteractionTimeout('timed out');
      expect(e.category).toBe(DomainErrorCategory.DISCORD_INTERACTION_TIMEOUT);
    });

    it('discordHookExpired', () => {
      const e = DomainError.discordHookExpired('hook expired');
      expect(e.category).toBe(DomainErrorCategory.DISCORD_HOOK_EXPIRED);
    });

    it('discordUnknownMessage', () => {
      const e = DomainError.discordUnknownMessage('unknown msg');
      expect(e.category).toBe(DomainErrorCategory.DISCORD_UNKNOWN_MESSAGE);
    });

    it('discordRateLimited', () => {
      const e = DomainError.discordRateLimited('rate limited');
      expect(e.category).toBe(DomainErrorCategory.DISCORD_RATE_LIMITED);
    });

    it('discordMissingPermissions', () => {
      const e = DomainError.discordMissingPermissions('no perms');
      expect(e.category).toBe(DomainErrorCategory.DISCORD_MISSING_PERMISSIONS);
    });

    it('discordInvalidComponentId', () => {
      const e = DomainError.discordInvalidComponentId('bad id');
      expect(e.category).toBe(
        DomainErrorCategory.DISCORD_INVALID_COMPONENT_ID,
      );
    });

    it('aiServiceTimeout', () => {
      const e = DomainError.aiServiceTimeout('ai timed out');
      expect(e.category).toBe(DomainErrorCategory.AI_SERVICE_TIMEOUT);
    });

    it('aiServiceAuthFailed', () => {
      const e = DomainError.aiServiceAuthFailed('auth failed');
      expect(e.category).toBe(DomainErrorCategory.AI_SERVICE_AUTH_FAILED);
    });

    it('aiServiceRateLimited', () => {
      const e = DomainError.aiServiceRateLimited('rate limited');
      expect(e.category).toBe(DomainErrorCategory.AI_SERVICE_RATE_LIMITED);
    });

    it('aiServiceUnavailable', () => {
      const e = DomainError.aiServiceUnavailable('unavailable');
      expect(e.category).toBe(DomainErrorCategory.AI_SERVICE_UNAVAILABLE);
    });

    it('aiResponseEmpty', () => {
      const e = DomainError.aiResponseEmpty('empty response');
      expect(e.category).toBe(DomainErrorCategory.AI_RESPONSE_EMPTY);
    });

    it('aiResponseInvalid', () => {
      const e = DomainError.aiResponseInvalid('invalid response');
      expect(e.category).toBe(DomainErrorCategory.AI_RESPONSE_INVALID);
    });

    it('promptDirNotFound', () => {
      const e = DomainError.promptDirNotFound('dir not found');
      expect(e.category).toBe(DomainErrorCategory.PROMPT_DIR_NOT_FOUND);
    });

    it('promptFileTooLarge', () => {
      const e = DomainError.promptFileTooLarge('too large');
      expect(e.category).toBe(DomainErrorCategory.PROMPT_FILE_TOO_LARGE);
    });

    it('promptReadFailed', () => {
      const cause = new Error('IO error');
      const e = DomainError.promptReadFailed('read failed', cause);
      expect(e.category).toBe(DomainErrorCategory.PROMPT_READ_FAILED);
      expect(e.cause).toBe(cause);
    });

    it('promptInvalidEncoding', () => {
      const e = DomainError.promptInvalidEncoding('bad encoding');
      expect(e.category).toBe(DomainErrorCategory.PROMPT_INVALID_ENCODING);
    });

    it('promptLoadFailed', () => {
      const cause = new Error('load error');
      const e = DomainError.promptLoadFailed('load failed', cause);
      expect(e.category).toBe(DomainErrorCategory.PROMPT_LOAD_FAILED);
      expect(e.cause).toBe(cause);
    });

    it('channelNotAllowed', () => {
      const e = DomainError.channelNotAllowed('not allowed');
      expect(e.category).toBe(DomainErrorCategory.CHANNEL_NOT_ALLOWED);
    });

    it('duplicateChannel', () => {
      const e = DomainError.duplicateChannel('duplicate');
      expect(e.category).toBe(DomainErrorCategory.DUPLICATE_CHANNEL);
    });

    it('insufficientPermissions', () => {
      const e = DomainError.insufficientPermissions('no perms');
      expect(e.category).toBe(DomainErrorCategory.INSUFFICIENT_PERMISSIONS);
    });

    it('channelNotFound', () => {
      const e = DomainError.channelNotFound('not found');
      expect(e.category).toBe(DomainErrorCategory.CHANNEL_NOT_FOUND);
    });

    it('duplicateCategory', () => {
      const e = DomainError.duplicateCategory('duplicate cat');
      expect(e.category).toBe(DomainErrorCategory.DUPLICATE_CATEGORY);
    });

    it('categoryNotFound', () => {
      const e = DomainError.categoryNotFound('cat not found');
      expect(e.category).toBe(DomainErrorCategory.CATEGORY_NOT_FOUND);
    });
  });

  describe('all categories exist', () => {
    it('has the correct enum values', () => {
      const values = Object.values(DomainErrorCategory);
      expect(values).toHaveLength(28);
      expect(values).toContain(DomainErrorCategory.INVALID_INPUT);
      expect(values).toContain(DomainErrorCategory.CATEGORY_NOT_FOUND);
    });
  });
});
