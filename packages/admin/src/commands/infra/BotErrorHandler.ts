import { type DiscordInteraction, DomainError, DomainErrorCategory } from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

/**
 * Maps Discord API error codes to user-facing messages.
 */
const DISCORD_ERROR_CODE_MAP: Record<number, string> = {
  10062: ZhTwStrings.discordError10062,
  50001: ZhTwStrings.discordError50001,
  50007: ZhTwStrings.discordError50007,
  30046: ZhTwStrings.discordError30046,
};

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
  async handle(error: unknown, interaction: DiscordInteraction): Promise<void> {
    const message = this.toUserMessage(error);

    try {
      if (interaction.isAcknowledged()) {
        const errorEmbed = new EmbedBuilder()
          .setTitle('錯誤')
          .setDescription(message)
          .setColor(0xed4245);
        await interaction.editEmbed(errorEmbed);
      } else {
        await interaction.reply(message);
      }
    } catch {
      // If replying also fails, there's nothing more we can do
      console.error('[BotErrorHandler] Failed to send error message:', error);
    }
  }

  /**
   * Converts an error to a user-friendly zh-TW message.
   */
  toUserMessage(error: unknown): string {
    if (error instanceof DomainError) {
      return this.handleDomainError(error);
    }

    // 合併 DiscordAPIError 與 DiscordAPIError-like 物件的錯誤碼查找（P3-19）
    const discordCode = extractDiscordErrorCode(error);
    if (discordCode != null) {
      const discordMsg = DISCORD_ERROR_CODE_MAP[discordCode] ?? null;
      if (discordMsg) {
        return discordMsg;
      }
    }

    // Log unexpected errors
    console.error(
      '[BotErrorHandler] Unexpected error:',
      error instanceof Error ? (error.stack ?? error.message) : String(error),
    );

    return ZhTwStrings.unexpectedError;
  }

  private handleDomainError(error: DomainError): string {
    const categoryMessage = ZhTwStrings.errorMapping[error.category];
    if (categoryMessage) {
      return categoryMessage;
    }
    return ZhTwStrings.unexpectedError;
  }
}

/**
 * Extracts a numeric Discord API error code from any error type.
 * Handles both discord.js native DiscordAPIError and DiscordAPIError-like objects.
 * Replaces the separate instanceof/isDiscordApiError checks (P3-19).
 */
function extractDiscordErrorCode(err: unknown): number | null {
  if (err && typeof err === 'object') {
    const obj = err as Record<string, unknown>;
    if (typeof obj.code === 'number') {
      return obj.code;
    }
    if (obj.code != null) {
      const parsed = Number(obj.code);
      if (Number.isFinite(parsed)) {
        return parsed;
      }
    }
  }
  return null;
}
