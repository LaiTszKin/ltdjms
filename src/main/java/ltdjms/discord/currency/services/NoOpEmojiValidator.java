package ltdjms.discord.currency.services;

import java.util.regex.Pattern;

/**
 * A no-op EmojiValidator that always returns true for valid-looking custom emoji patterns.
 * Used for integration tests where JDA is not available.
 */
public class NoOpEmojiValidator implements EmojiValidator {

    /**
     * Pattern to validate Discord custom emoji format.
     * Matches: {@code <:name:id>} or {@code <a:name:id>}
     * where name is 2-32 word characters and id is a snowflake (17-20 digits).
     */
    private static final Pattern CUSTOM_EMOJI_PATTERN =
            Pattern.compile("^<a?:\\w{2,32}:\\d{17,20}>$");

    @Override
    public boolean isValidCustomEmoji(String emojiMarkup) {
        if (emojiMarkup == null || emojiMarkup.isBlank()) {
            return false;
        }
        // For testing purposes, validate format only
        return CUSTOM_EMOJI_PATTERN.matcher(emojiMarkup).matches();
    }
}
