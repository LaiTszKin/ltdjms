import { AdminPanelSlashCommand } from '../../panel/admin/definitions/AdminPanelSlashCommand.js';
import { UserPanelSlashCommand } from '../../panel/user/definitions/UserPanelSlashCommand.js';
// ============================================================
// Stub definitions for commands not yet implemented.
// Each entry provides the name, zh-TW description, and
// defaultMemberPermissions for Discord API registration.
// ============================================================
// ============================================================
// Economy Module — member-facing commands
// ============================================================
/** /balance — view own balance (available to all members). */
const BalanceSlashCommand = {
    name: 'balance',
    description: '查看自己的餘額',
    defaultMemberPermissions: null,
    nameLocalizations: { 'zh-TW': '餘額查詢' },
    descriptionLocalizations: { 'zh-TW': '查看自己的貨幣餘額' },
};
/** /dice-game-1 — play Dice Game 1 (available to all members). */
const DiceGame1SlashCommand = {
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
const DiceGame2SlashCommand = {
    name: 'dice-game-2',
    description: '骰子遊戲 2',
    defaultMemberPermissions: null,
    nameLocalizations: { 'zh-TW': '骰子遊戲 2' },
    descriptionLocalizations: { 'zh-TW': '使用遊戲代幣進行骰子遊戲 2' },
    options: [
        { name: 'tokens', description: '要使用的代幣數量', type: 4, required: true },
    ],
};
/** /shop — open the guild shop (available to all members). */
const ShopSlashCommand = {
    name: 'shop',
    description: '開啟商城',
    defaultMemberPermissions: null,
    nameLocalizations: { 'zh-TW': '商城' },
    descriptionLocalizations: { 'zh-TW': '瀏覽與購買商品' },
};
/** /redeem-code — redeem a code (available to all members). */
// TODO(P1-39): Add RedeemCodeSlashCommand definition once the redeem-code
// slash command is implemented in the shop module.
// ============================================================
// Admin Module — admin-only commands
// ============================================================
/** /admin-panel — open the admin panel (admin only). */
// AdminPanelSlashCommand is defined in AdminPanelSlashCommand.ts under panel/admin/definitions/.
/** /adjust-balance — admin adjusts a member's balance. */
const AdjustBalanceSlashCommand = {
    name: 'adjust-balance',
    description: '調整成員餘額',
    defaultMemberPermissions: '8',
    nameLocalizations: { 'zh-TW': '調整餘額' },
    descriptionLocalizations: { 'zh-TW': '調整指定成員的貨幣餘額（管理員專用）' },
};
/** /game-token-adjust — admin adjusts a member's game tokens. */
const GameTokenAdjustSlashCommand = {
    name: 'game-token-adjust',
    description: '調整成員遊戲代幣',
    defaultMemberPermissions: '8',
    nameLocalizations: { 'zh-TW': '調整代幣' },
    descriptionLocalizations: { 'zh-TW': '調整指定成員的遊戲代幣數量（管理員專用）' },
};
/** /dice-game-1-config — configure Dice Game 1 parameters. */
const DiceGame1ConfigSlashCommand = {
    name: 'dice-game-1-config',
    description: '設定骰子遊戲 1 參數',
    defaultMemberPermissions: '8',
    nameLocalizations: { 'zh-TW': '骰子遊戲 1 設定' },
    descriptionLocalizations: { 'zh-TW': '設定骰子遊戲 1 的參數（管理員專用）' },
};
/** /dice-game-2-config — configure Dice Game 2 parameters. */
const DiceGame2ConfigSlashCommand = {
    name: 'dice-game-2-config',
    description: '設定骰子遊戲 2 參數',
    defaultMemberPermissions: '8',
    nameLocalizations: { 'zh-TW': '骰子遊戲 2 設定' },
    descriptionLocalizations: { 'zh-TW': '設定骰子遊戲 2 的參數（管理員專用）' },
};
/** /currency-config — configure guild currency settings (admin only). */
const CurrencyConfigSlashCommand = {
    name: 'currency-config',
    description: '設定貨幣參數',
    defaultMemberPermissions: '8',
    nameLocalizations: { 'zh-TW': '貨幣設定' },
    descriptionLocalizations: { 'zh-TW': '設定伺服器貨幣名稱與圖示（管理員專用）' },
};
// ============================================================
// Dispatch Module — admin-only commands
// ============================================================
/** /dispatch-panel — open the dispatch management panel (admin only). */
const DispatchPanelSlashCommand = {
    name: 'dispatch-panel',
    description: '開啟派單管理面板',
    defaultMemberPermissions: '8',
    nameLocalizations: { 'zh-TW': '派單面板' },
    descriptionLocalizations: { 'zh-TW': '開啟護航派單管理面板（管理員專用）' },
};
/**
 * Aggregate of all slash command definitions across all packages.
 * Used by the registration script to bulk-register commands with Discord API.
 */
const ALL_COMMAND_DEFINITIONS = [
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
    static getAllDefinitions() {
        return [...ALL_COMMAND_DEFINITIONS];
    }
    /**
     * Registers all commands globally via the provided REST client.
     * Typically called with discord.js REST.put().
     * @param applicationId - Discord application ID
     * @param restPut - Function that calls REST.put() with the given route and body
     * @param guildId - Optional guild ID for development (guild-specific registration)
     */
    static async registerAll(applicationId, restPut, guildId) {
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
        }
        catch (err) {
            const errorMessage = err instanceof Error ? err.message : String(err);
            return {
                success: false,
                message: `Failed to register commands: ${errorMessage}`,
            };
        }
    }
}
//# sourceMappingURL=SlashCommandRegistrar.js.map