package ltdjms.discord.currency.services;

/**
 * Interface for validating Discord custom emoji markup. Allows for different implementations for
 * production (JDA-based) and testing (mock).
 */
public interface EmojiValidator {

  /**
   * Validates a Discord custom emoji markup string. Custom emoji format: {@code <:name:id>} or
   * {@code <a:name:id>} for animated.
   *
   * @param emojiMarkup the emoji markup string to validate
   * @return true if the emoji is valid and can be used, false otherwise
   */
  boolean isValidCustomEmoji(String emojiMarkup);
}
