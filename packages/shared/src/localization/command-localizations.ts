export const CommandLocalizations = {
  // ============================================================
  // Command names (12 commands)
  // ============================================================
  COMMAND_NAME_LOCALIZATIONS: {
    balance: { 'zh-TW': '餘額' },
    'currency-config': { 'zh-TW': '貨幣設定' },
    'adjust-balance': { 'zh-TW': '調整餘額' },
    'game-token-adjust': { 'zh-TW': '調整代幣' },
    'dice-game-1': { 'zh-TW': '骰子遊戲1' },
    'dice-game-1-config': { 'zh-TW': '骰子遊戲1設定' },
    'dice-game-2': { 'zh-TW': '骰子遊戲2' },
    'dice-game-2-config': { 'zh-TW': '骰子遊戲2設定' },
    'user-panel': { 'zh-TW': '用戶面板' },
    'admin-panel': { 'zh-TW': '管理面板' },
    shop: { 'zh-TW': '商城' },
    'dispatch-panel': { 'zh-TW': '派單面板' },
  } as const,

  // ============================================================
  // Command descriptions (12 commands)
  // ============================================================
  COMMAND_DESCRIPTION_LOCALIZATIONS: {
    balance: { 'zh-TW': '查看自己的貨幣餘額' },
    'currency-config': { 'zh-TW': '設定伺服器貨幣名稱與圖示（管理員專用）' },
    'adjust-balance': { 'zh-TW': '調整指定成員的貨幣餘額（管理員專用）' },
    'game-token-adjust': { 'zh-TW': '調整指定成員的遊戲代幣數量（管理員專用）' },
    'dice-game-1': { 'zh-TW': '使用遊戲代幣進行骰子遊戲 1' },
    'dice-game-1-config': { 'zh-TW': '設定骰子遊戲 1 的參數（管理員專用）' },
    'dice-game-2': { 'zh-TW': '使用遊戲代幣進行骰子遊戲 2' },
    'dice-game-2-config': { 'zh-TW': '設定骰子遊戲 2 的參數（管理員專用）' },
    'user-panel': { 'zh-TW': '查看餘額、代幣、交易記錄與兌換兌換碼' },
    'admin-panel': { 'zh-TW': '開啟管理面板，管理貨幣、代幣、遊戲、產品、AI 等設定' },
    shop: { 'zh-TW': '瀏覽與購買商品' },
    'dispatch-panel': { 'zh-TW': '開啟護航派單管理面板（管理員專用）' },
  } as const,

  // ============================================================
  // Option names (7 options)
  // ============================================================
  OPTION_NAME_LOCALIZATIONS: {
    name: { 'zh-TW': '名稱' },
    icon: { 'zh-TW': '圖示' },
    mode: { 'zh-TW': '模式' },
    member: { 'zh-TW': '成員' },
    amount: { 'zh-TW': '數量' },
    'token-cost': { 'zh-TW': '代幣消耗' },
    tokens: { 'zh-TW': '代幣' },
  } as const,

  // ============================================================
  // Option descriptions (7 options)
  // ============================================================
  OPTION_DESCRIPTION_LOCALIZATIONS: {
    name: { 'zh-TW': '貨幣名稱' },
    icon: { 'zh-TW': '貨幣圖示（emoji）' },
    mode: { 'zh-TW': '調整模式' },
    member: { 'zh-TW': '選擇成員' },
    amount: { 'zh-TW': '調整數量' },
    'token-cost': { 'zh-TW': '每次遊戲消耗的代幣數量' },
    tokens: { 'zh-TW': '要使用的代幣數量' },
  } as const,

  // ============================================================
  // Choice values (3 choices)
  // ============================================================
  CHOICE_LOCALIZATIONS: {
    add: { 'zh-TW': '增加' },
    deduct: { 'zh-TW': '扣減' },
    adjust: { 'zh-TW': '調整' },
  } as const,

  // ============================================================
  // Accessor methods
  // ============================================================
} as const;

// Type helpers
export type CommandName = keyof typeof CommandLocalizations.COMMAND_NAME_LOCALIZATIONS;
export type OptionName = keyof typeof CommandLocalizations.OPTION_NAME_LOCALIZATIONS;
export type ChoiceValue = keyof typeof CommandLocalizations.CHOICE_LOCALIZATIONS;

// Accessor functions
export function getCommandNameLocalization(cmd: CommandName): Record<string, string> {
  return CommandLocalizations.COMMAND_NAME_LOCALIZATIONS[cmd];
}

export function getCommandDescriptionLocalization(cmd: CommandName): Record<string, string> {
  return CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS[cmd];
}

export function getOptionNameLocalization(opt: OptionName): Record<string, string> {
  return CommandLocalizations.OPTION_NAME_LOCALIZATIONS[opt];
}

export function getOptionDescriptionLocalization(opt: OptionName): Record<string, string> {
  return CommandLocalizations.OPTION_DESCRIPTION_LOCALIZATIONS[opt];
}

export function getChoiceLocalization(choice: ChoiceValue): Record<string, string> {
  return CommandLocalizations.CHOICE_LOCALIZATIONS[choice];
}
