package ltdjms.discord.redemption.services;

import java.security.SecureRandom;

/**
 * Generates cryptographically secure redemption codes.
 * Uses SecureRandom for randomness and a character set that excludes confusing characters.
 */
public class RedemptionCodeGenerator {

    /**
     * Length of the generated redemption code.
     */
    public static final int CODE_LENGTH = 16;

    /**
     * Characters used for generating redemption codes.
     * Excludes confusing characters: 0/O, 1/I/L
     */
    private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final SecureRandom secureRandom;

    public RedemptionCodeGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Constructor for testing purposes.
     *
     * @param secureRandom a SecureRandom instance
     */
    public RedemptionCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * Generates a single redemption code.
     *
     * @return a 16-character uppercase alphanumeric code
     */
    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        return code.toString();
    }

    /**
     * Validates if a string is a valid redemption code format.
     *
     * @param code the code to validate
     * @return true if the code matches the expected format
     */
    public static boolean isValidFormat(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (char c : code.toUpperCase().toCharArray()) {
            if (CHARACTERS.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }
}
