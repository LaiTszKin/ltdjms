/**
 * zh-TW strings for the user panel package.
 */
export const ZhTwStrings = {
  sessionExpired: '面板已過期，請重新執行指令',
  unexpectedError: '發生未預期的錯誤，請聯絡管理員',

  userPanelTitle: '個人面板',
  userPanelDescription: '{mention} 的帳戶資訊',
  userPanelCurrencyBalanceField: '{currencyName}餘額',
  userPanelTokenBalanceField: '遊戲代幣餘額',
  userPanelGameTokenName: '遊戲代幣',

  userPanelBtnCurrencyHistory: '{icon} 查看貨幣流水',
  userPanelBtnTokenHistory: '📜 查看遊戲代幣流水',
  userPanelBtnRedemptionHistory: '🛒 查看商品流水',
  userPanelBtnRedeemCode: '🎫 兌換碼',

  historyTitleCurrency: '💰 貨幣流水',
  historyTitleToken: '📜 遊戲代幣流水',
  historyTitleRedemption: '🛒 商品流水',
  historyEmptyCurrency: '目前沒有任何貨幣流水紀錄',
  historyEmptyToken: '目前沒有任何遊戲代幣流水紀錄',
  historyEmptyRedemption: '目前沒有任何商品兌換紀錄',
  historyPageIndicator: '第 {current}/{total} 頁（共 {count} 筆）',
  historyBackBtn: '🔙 返回主頁',
  historyPrevBtn: '⬅️ 上一頁',
  historyNextBtn: '下一頁 ➡️',

  redeemCodeModalTitle: '兌換碼',
  redeemCodeLabel: '兌換碼',
  redeemCodePlaceholder: '請輸入 16 位數兌換碼',
  redeemFailurePrefix: '❌ 兌換失敗：',
  redeemSuccessPrefix: '✅ ',
  redeemEmptyCode: '❌ 兌換失敗：請輸入有效的兌換碼',
  genericError: '發生錯誤，請稍後再試',
  guildOnly: '此功能只能在伺服器中使用',
  loadPanelFailed: '無法載入面板資料，請稍後再試',
} as const;

export function formatUserPanelDescription(mention: string): string {
  return ZhTwStrings.userPanelDescription.replace('{mention}', mention);
}

export function formatCurrencyHistoryButtonLabel(icon: string): string {
  return ZhTwStrings.userPanelBtnCurrencyHistory.replace('{icon}', icon);
}

export function formatCurrencyBalanceFieldName(currencyName: string): string {
  return ZhTwStrings.userPanelCurrencyBalanceField.replace('{currencyName}', currencyName);
}

export function formatHistoryPageIndicator(current: number, total: number, count: number): string {
  return ZhTwStrings.historyPageIndicator
    .replace('{current}', String(current))
    .replace('{total}', String(total))
    .replace('{count}', String(count));
}
