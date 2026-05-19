import { PermissionFlagsBits, type Guild, type GuildMember } from 'discord.js';
import { ToolExecutionContext } from './ToolExecutionContext.js';

/**
 * Validates that the tool caller has ADMINISTRATOR permission or is the guild owner.
 * Matches Java ToolCallerAuthorizationGuard.
 */
export class ToolCallerAuthorizationGuard {
  /**
   * Validates that the caller has administrator permissions.
   *
   * @param guild - The Discord guild
   * @param toolName - The tool name for logging
   * @returns null if authorized, or an error message string if unauthorized
   */
  async validateAdministrator(
    guild: Guild,
    toolName: string,
  ): Promise<string | null> {
    const context = ToolExecutionContext.getContext();
    if (!context) {
      return '工具執行上下文遺失，無法驗證權限。';
    }

    const { userId } = context;
    if (!userId) {
      return '無法識別使用者身份。';
    }

    // Check if user is guild owner
    if (guild.ownerId === userId) {
      return null;
    }

    try {
      // Try to fetch the member
      let member: GuildMember | null = null;

      // Check cache first
      if (guild.members.cache.has(userId)) {
        member = guild.members.cache.get(userId)!;
      } else {
        // Fetch from API
        member = await guild.members.fetch(userId);
      }

      // Check ADMINISTRATOR permission
      if (member.permissions.has(PermissionFlagsBits.Administrator)) {
        return null;
      }

      console.warn(
        `[Auth] Non-admin user ${userId} attempted to use tool ${toolName} in guild ${guild.id}`,
      );
      return '你沒有權限使用此工具。';
    } catch {
      console.warn(
        `[Auth] Member not found for user ${userId} in guild ${guild.id} attempting tool ${toolName}`,
      );
      return '無法在伺服器中找到你的成員資料。';
    }
  }
}
