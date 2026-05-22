import type { SlashCommandDefinition } from './SlashCommandRegistrar.js';

/** /balance — view own balance (available to all members). */
export const BalanceSlashCommand: SlashCommandDefinition = {
  name: 'balance',
  description: '查看自己的餘額',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '餘額查詢' },
  descriptionLocalizations: { 'zh-TW': '查看自己的貨幣餘額' },
};

/** /adjust-balance — admin adjusts a member's balance. */
export const AdjustBalanceSlashCommand: SlashCommandDefinition = {
  name: 'adjust-balance',
  description: '調整成員餘額',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '調整餘額' },
  descriptionLocalizations: { 'zh-TW': '調整指定成員的貨幣餘額（管理員專用）' },
};

/** /currency-config — configure guild currency settings (admin only). */
export const CurrencyConfigSlashCommand: SlashCommandDefinition = {
  name: 'currency-config',
  description: '設定貨幣參數',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '貨幣設定' },
  descriptionLocalizations: { 'zh-TW': '設定伺服器貨幣名稱與圖示（管理員專用）' },
};

/** /dice-game-1 — play Dice Game 1 (available to all members). */
export const DiceGame1SlashCommand: SlashCommandDefinition = {
  name: 'dice-game-1',
  description: '骰子遊戲 1',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '骰子遊戲1' },
  descriptionLocalizations: { 'zh-TW': '使用遊戲代幣進行骰子遊戲 1' },
  options: [
    { name: 'tokens', description: '要使用的代幣數量', type: 4, required: true },
  ],
};

/** /dice-game-2 — play Dice Game 2 (available to all members). */
export const DiceGame2SlashCommand: SlashCommandDefinition = {
  name: 'dice-game-2',
  description: '骰子遊戲 2',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '骰子遊戲2' },
  descriptionLocalizations: { 'zh-TW': '使用遊戲代幣進行骰子遊戲 2' },
  options: [
    { name: 'tokens', description: '要使用的代幣數量', type: 4, required: true },
  ],
};

/** /dice-game-1-config — configure Dice Game 1 parameters. */
export const DiceGame1ConfigSlashCommand: SlashCommandDefinition = {
  name: 'dice-game-1-config',
  description: '設定骰子遊戲 1 參數',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '骰子遊戲1設定' },
  descriptionLocalizations: { 'zh-TW': '設定骰子遊戲 1 的參數（管理員專用）' },
};

/** /dice-game-2-config — configure Dice Game 2 parameters. */
export const DiceGame2ConfigSlashCommand: SlashCommandDefinition = {
  name: 'dice-game-2-config',
  description: '設定骰子遊戲 2 參數',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '骰子遊戲2設定' },
  descriptionLocalizations: { 'zh-TW': '設定骰子遊戲 2 的參數（管理員專用）' },
};

/** /game-token-adjust — admin adjusts a member's game tokens. */
export const GameTokenAdjustSlashCommand: SlashCommandDefinition = {
  name: 'game-token-adjust',
  description: '調整成員遊戲代幣',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '調整代幣' },
  descriptionLocalizations: { 'zh-TW': '調整指定成員的遊戲代幣數量（管理員專用）' },
};
