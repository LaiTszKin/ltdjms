import { CommandLocalizations } from '@ltdjms/shared';
import type { SlashCommandDefinition } from './SlashCommandRegistrar.js';

/** /balance — view own balance (available to all members). */
export const BalanceSlashCommand: SlashCommandDefinition = {
  name: 'balance',
  description: '查看自己的餘額',
  defaultMemberPermissions: null,
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['balance'],
  descriptionLocalizations: CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['balance'],
};

/** /adjust-balance — admin adjusts a member's balance. */
export const AdjustBalanceSlashCommand: SlashCommandDefinition = {
  name: 'adjust-balance',
  description: '調整成員餘額',
  defaultMemberPermissions: '8',
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['adjust-balance'],
  descriptionLocalizations:
    CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['adjust-balance'],
};

/** /currency-config — configure guild currency settings (admin only). */
export const CurrencyConfigSlashCommand: SlashCommandDefinition = {
  name: 'currency-config',
  description: '設定貨幣參數',
  defaultMemberPermissions: '8',
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['currency-config'],
  descriptionLocalizations:
    CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['currency-config'],
};

/** /dice-game-1 — play Dice Game 1 (available to all members). */
export const DiceGame1SlashCommand: SlashCommandDefinition = {
  name: 'dice-game-1',
  description: '骰子遊戲 1',
  defaultMemberPermissions: null,
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['dice-game-1'],
  descriptionLocalizations: CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['dice-game-1'],
  options: [
    {
      name: 'tokens',
      description: '要使用的代幣數量',
      descriptionLocalizations:
        CommandLocalizations.OPTION_DESCRIPTION_LOCALIZATIONS['tokens'],
      type: 4,
      required: true,
    },
  ],
};

/** /dice-game-2 — play Dice Game 2 (available to all members). */
export const DiceGame2SlashCommand: SlashCommandDefinition = {
  name: 'dice-game-2',
  description: '骰子遊戲 2',
  defaultMemberPermissions: null,
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['dice-game-2'],
  descriptionLocalizations: CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['dice-game-2'],
  options: [
    {
      name: 'tokens',
      description: '要使用的代幣數量',
      descriptionLocalizations:
        CommandLocalizations.OPTION_DESCRIPTION_LOCALIZATIONS['tokens'],
      type: 4,
      required: true,
    },
  ],
};

/** /dice-game-1-config — configure Dice Game 1 parameters. */
export const DiceGame1ConfigSlashCommand: SlashCommandDefinition = {
  name: 'dice-game-1-config',
  description: '設定骰子遊戲 1 參數',
  defaultMemberPermissions: '8',
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['dice-game-1-config'],
  descriptionLocalizations:
    CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['dice-game-1-config'],
};

/** /dice-game-2-config — configure Dice Game 2 parameters. */
export const DiceGame2ConfigSlashCommand: SlashCommandDefinition = {
  name: 'dice-game-2-config',
  description: '設定骰子遊戲 2 參數',
  defaultMemberPermissions: '8',
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['dice-game-2-config'],
  descriptionLocalizations:
    CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['dice-game-2-config'],
};

/** /game-token-adjust — admin adjusts a member's game tokens. */
export const GameTokenAdjustSlashCommand: SlashCommandDefinition = {
  name: 'game-token-adjust',
  description: '調整成員遊戲代幣',
  defaultMemberPermissions: '8',
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['game-token-adjust'],
  descriptionLocalizations:
    CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['game-token-adjust'],
};
