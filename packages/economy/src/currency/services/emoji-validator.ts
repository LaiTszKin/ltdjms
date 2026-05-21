import { type Result, Err, okVoid, DomainError } from '@ltdjms/shared';
import { MAX_CURRENCY_ICON_LENGTH } from '../../domain/types.js';

/**
 * Canonical pattern to detect Discord custom emoji format.
 * Matches `<:name:id>` or `<a:name:id>` where id is numeric.
 */
export const CUSTOM_EMOJI_PATTERN = /^<a?:[^:]+:\d+>$/;

/**
 * Checks whether a string "looks like" a Discord custom emoji attempt.
 * Uses a loose heuristic (starts with `<`) so that the strict {@link CUSTOM_EMOJI_PATTERN}
 * validation has a chance to reject malformed custom emoji strings.
 * Plain Unicode emoji (e.g. 🪙) do NOT start with `<` and bypass the custom emoji check.
 */
export function looksLikeCustomEmoji(icon: string): boolean {
  return icon.startsWith('<');
}

/**
 * Validates Discord emoji format for currency configuration.
 */
export class EmojiValidator {
  /**
   * Returns true if the value matches Discord's custom emoji format (`<:name:id>` or `<a:name:id>`).
   */
  isValidEmoji(value: string): boolean {
    return CUSTOM_EMOJI_PATTERN.test(value);
  }

  /**
   * Validates a currency icon string.
   * Checks for blank, max length, and custom emoji format.
   */
  validate(icon: string): Result<import('@ltdjms/shared').Unit, DomainError> {
    if (!icon || icon.trim().length === 0) {
      return new Err(DomainError.invalidInput('Currency icon cannot be blank'));
    }
    if (icon.length > MAX_CURRENCY_ICON_LENGTH) {
      return new Err(
        DomainError.invalidInput(
          `Currency icon cannot exceed ${MAX_CURRENCY_ICON_LENGTH} characters`,
        ),
      );
    }

    if (looksLikeCustomEmoji(icon)) {
      if (!CUSTOM_EMOJI_PATTERN.test(icon)) {
        return new Err(
          DomainError.invalidInput(
            `Invalid Discord custom emoji: '${icon}'. Please ensure the emoji exists and is accessible.`,
          ),
        );
      }
    }

    return okVoid();
  }
}
