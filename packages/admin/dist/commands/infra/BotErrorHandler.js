import { DomainError, } from '@ltdjms/shared';
import { ZhTwStrings } from '../../i18n/zh-TW.js';
/**
 * Maps Discord API error codes to user-facing messages.
 */
const DISCORD_ERROR_CODE_MAP = {
    10062: ZhTwStrings.discordError10062,
    50001: ZhTwStrings.discordError50001,
    50007: ZhTwStrings.discordError50007,
    30046: ZhTwStrings.discordError30046,
};
/**
 * Checks if an error code has a known mapping.
 */
function getDiscordErrorMessage(code) {
    return DISCORD_ERROR_CODE_MAP[code] ?? null;
}
/**
 * Centralized error handler for bot interactions.
 * Converts DomainError, DiscordAPIError, and unexpected errors
 * to user-friendly zh-TW messages.
 * Matches Java BotErrorHandler.
 */
export class BotErrorHandler {
    /**
     * Handles an error by replying to the interaction with a user-friendly message.
     * @param error - The error to handle
     * @param interaction - The Discord interaction to reply to
     */
    async handle(error, interaction) {
        const message = this.toUserMessage(error);
        try {
            if (interaction.isAcknowledged()) {
                await interaction.editEmbed({
                    description: message,
                    color: 0xED4245,
                    title: '錯誤',
                });
            }
            else {
                await interaction.reply(message);
            }
        }
        catch {
            // If replying also fails, there's nothing more we can do
            console.error('[BotErrorHandler] Failed to send error message:', error);
        }
    }
    /**
     * Converts an error to a user-friendly zh-TW message.
     */
    toUserMessage(error) {
        if (error instanceof DomainError) {
            return this.handleDomainError(error);
        }
        // Check for DiscordAPIError-like objects
        if (isDiscordApiError(error)) {
            const discordMsg = getDiscordErrorMessage(error.code);
            if (discordMsg) {
                return discordMsg;
            }
        }
        // Log unexpected errors
        console.error('[BotErrorHandler] Unexpected error:', error instanceof Error ? error.stack ?? error.message : String(error));
        return ZhTwStrings.unexpectedError;
    }
    handleDomainError(error) {
        const categoryMessage = ZhTwStrings.errorMapping[error.category];
        if (categoryMessage) {
            // Append the error detail if available and different from category message
            if (error.message && error.message !== categoryMessage) {
                return `${categoryMessage}（${error.message}）`;
            }
            return categoryMessage;
        }
        return error.message || ZhTwStrings.unexpectedError;
    }
}
/**
 * Type guard for Discord API error-like objects.
 */
function isDiscordApiError(err) {
    if (err && typeof err === 'object') {
        const obj = err;
        return typeof obj.code === 'number';
    }
    return false;
}
//# sourceMappingURL=BotErrorHandler.js.map