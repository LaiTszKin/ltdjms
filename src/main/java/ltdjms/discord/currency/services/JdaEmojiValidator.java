package ltdjms.discord.currency.services;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * JDA-based implementation of EmojiValidator. Uses JDA's Emoji.fromFormatted() to parse and
 * validate custom emoji markup.
 */
public class JdaEmojiValidator implements EmojiValidator {

  private static final Logger LOG = LoggerFactory.getLogger(JdaEmojiValidator.class);

  /**
   * Pattern to match Discord custom emoji format. Matches: {@code <:name:id>} or {@code
   * <a:name:id>} where name is 2-32 word characters and id is a snowflake (17-20 digits).
   */
  private static final Pattern CUSTOM_EMOJI_PATTERN =
      Pattern.compile("^<a?:\\w{2,32}:\\d{17,20}>$");

  @Override
  public boolean isValidCustomEmoji(String emojiMarkup) {
    if (emojiMarkup == null || emojiMarkup.isBlank()) {
      return false;
    }

    // First check if it matches the custom emoji format pattern
    if (!CUSTOM_EMOJI_PATTERN.matcher(emojiMarkup).matches()) {
      return false;
    }

    try {
      // Use JDA to parse the emoji markup
      EmojiUnion emoji = Emoji.fromFormatted(emojiMarkup);

      // Verify it was parsed as a custom emoji
      if (emoji instanceof CustomEmoji) {
        LOG.debug("Valid custom emoji: {}", emojiMarkup);
        return true;
      }

      LOG.debug("Emoji parsed but not a custom emoji: {}", emojiMarkup);
      return false;
    } catch (Exception e) {
      LOG.debug("Failed to parse emoji markup '{}': {}", emojiMarkup, e.getMessage());
      return false;
    }
  }
}
