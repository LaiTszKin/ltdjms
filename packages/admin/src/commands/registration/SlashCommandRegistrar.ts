import { AdminPanelSlashCommand } from '../../panel/admin/definitions/AdminPanelSlashCommand.js';
import { UserPanelSlashCommand } from '../../panel/user/definitions/UserPanelSlashCommand.js';

/**
 * A slash command definition suitable for Discord API registration.
 */
export interface SlashCommandDefinition {
  name: string;
  description: string;
  options?: unknown[];
  defaultMemberPermissions?: string | null;
  nameLocalizations?: Record<string, string>;
  descriptionLocalizations?: Record<string, string>;
}

// ============================================================
// Stub definitions for commands not yet implemented.
// Each entry provides the name, zh-TW description, and
// defaultMemberPermissions for Discord API registration.
// ============================================================

/** /balance — view own balance (available to all members). */
const BalanceSlashCommand: SlashCommandDefinition = {
  name: 'balance',
  description: '查看自己的餘額',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '餘額查詢' },
  descriptionLocalizations: { 'zh-TW': '查看自己的貨幣餘額' },
};

/** /adjust-balance — admin adjusts a member's balance. */
const AdjustBalanceSlashCommand: SlashCommandDefinition = {
  name: 'adjust-balance',
  description: '調整成員餘額',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '調整餘額' },
  descriptionLocalizations: { 'zh-TW': '調整指定成員的貨幣餘額（管理員專用）' },
};

/** /game-token-adjust — admin adjusts a member's game tokens. */
const GameTokenAdjustSlashCommand: SlashCommandDefinition = {
  name: 'game-token-adjust',
  description: '調整成員遊戲代幣',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '調整代幣' },
  descriptionLocalizations: { 'zh-TW': '調整指定成員的遊戲代幣數量（管理員專用）' },
};

/** /dice-game-1 — play Dice Game 1 (available to all members). */
const DiceGame1SlashCommand: SlashCommandDefinition = {
  name: 'dice-game-1',
  description: '骰子遊戲 1',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '骰子遊戲 1' },
  descriptionLocalizations: { 'zh-TW': '使用遊戲代幣進行骰子遊戲 1' },
  options: [
    { name: 'tokens', description: '要使用的代幣數量', type: 4, required: true },
  ],
};

/** /dice-game-2 — play Dice Game 2 (available to all members). */
const DiceGame2SlashCommand: SlashCommandDefinition = {
  name: 'dice-game-2',
  description: '骰子遊戲 2',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '骰子遊戲 2' },
  descriptionLocalizations: { 'zh-TW': '使用遊戲代幣進行骰子遊戲 2' },
  options: [
    { name: 'tokens', description: '要使用的代幣數量', type: 4, required: true },
  ],
};

/** /dice-game-1-config — configure Dice Game 1 parameters. */
const DiceGame1ConfigSlashCommand: SlashCommandDefinition = {
  name: 'dice-game-1-config',
  description: '設定骰子遊戲 1 參數',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '骰子遊戲 1 設定' },
  descriptionLocalizations: { 'zh-TW': '設定骰子遊戲 1 的參數（管理員專用）' },
};

/** /dice-game-2-config — configure Dice Game 2 parameters. */
const DiceGame2ConfigSlashCommand: SlashCommandDefinition = {
  name: 'dice-game-2-config',
  description: '設定骰子遊戲 2 參數',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '骰子遊戲 2 設定' },
  descriptionLocalizations: { 'zh-TW': '設定骰子遊戲 2 的參數（管理員專用）' },
};

/** /shop — open the guild shop (available to all members). */
const ShopSlashCommand: SlashCommandDefinition = {
  name: 'shop',
  description: '開啟商城',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '商城' },
  descriptionLocalizations: { 'zh-TW': '瀏覽與購買商品' },
};

/** /dispatch-panel — open the dispatch management panel (admin only). */
const DispatchPanelSlashCommand: SlashCommandDefinition = {
  name: 'dispatch-panel',
  description: '開啟派單管理面板',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '派單面板' },
  descriptionLocalizations: { 'zh-TW': '開啟護航派單管理面板（管理員專用）' },
};

/** /currency-config — configure guild currency settings (admin only). */
const CurrencyConfigSlashCommand: SlashCommandDefinition = {
  name: 'currency-config',
  description: '設定貨幣參數',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '貨幣設定' },
  descriptionLocalizations: { 'zh-TW': '設定伺服器貨幣名稱與圖示（管理員專用）' },
};

/**
 * Aggregate of all slash command definitions across all packages.
 * Used by the registration script to bulk-register commands with Discord API.
 */
const ALL_COMMAND_DEFINITIONS: SlashCommandDefinition[] = [
  // Admin commands
  AdminPanelSlashCommand,
  UserPanelSlashCommand,
  BalanceSlashCommand,
  AdjustBalanceSlashCommand,
  DiceGame1SlashCommand,
  DiceGame2SlashCommand,
  GameTokenAdjustSlashCommand,
  DiceGame1ConfigSlashCommand,
  DiceGame2ConfigSlashCommand,
  ShopSlashCommand,
  DispatchPanelSlashCommand,
  CurrencyConfigSlashCommand,
];

/**
 * Registers slash commands with the Discord API via a REST client.
 * Matches Java SlashCommandRegistrar.
 */
export class SlashCommandRegistrar {
  /**
   * Returns all command definitions that should be registered.
   */
  static getAllDefinitions(): SlashCommandDefinition[] {
    return [...ALL_COMMAND_DEFINITIONS];
  }

  /**
   * Registers all commands globally via the provided REST client.
   * Typically called with discord.js REST.put().
   * @param applicationId - Discord application ID
   * @param restPut - Function that calls REST.put() with the given route and body
   * @param guildId - Optional guild ID for development (guild-specific registration)
   */
  static async registerAll(
    applicationId: string,
    restPut: (route: string, body: unknown) => Promise<unknown>,
    guildId?: string,
  ): Promise<{ success: boolean; message: string }> {
    try {
      const definitions = ALL_COMMAND_DEFINITIONS.map((def) => ({
        name: def.name,
        name_localizations: def.nameLocalizations ?? {},
        description: def.description,
        description_localizations: def.descriptionLocalizations ?? {},
        options: def.options ?? [],
        default_member_permissions: def.defaultMemberPermissions ?? null,
      }));

      const route = guildId
        ? `/applications/${applicationId}/guilds/${guildId}/commands`
        : `/applications/${applicationId}/commands`;

      const result = await restPut(route, definitions);
      const count = Array.isArray(result) ? result.length : 0;

      return {
        success: true,
        message: `Successfully registered ${count} commands${guildId ? ` in guild ${guildId}` : ' globally'}`,
      };
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : String(err);
      return {
        success: false,
        message: `Failed to register commands: ${errorMessage}`,
      };
    }
  }
}
