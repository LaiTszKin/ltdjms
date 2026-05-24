/**
 * zh-TW strings for the user panel package.
 */
export const ZhTwStrings = {
  sessionExpired: '面板已過期，請重新執行指令',
  unexpectedError: '發生未預期的錯誤，請聯絡管理員',

  userPanelTitle: '用戶面板',
  userPanelBalance: '貨幣餘額：**{balance}** {currencyIcon}',
  userPanelTokens: '遊戲代幣：**{tokens}** 個',

  userPanelBtnCurrencyHistory: '貨幣記錄',
  userPanelBtnTokenHistory: '代幣記錄',
  userPanelBtnRedemptionHistory: '兌換記錄',
  userPanelBtnRedeemCode: '輸入兌換碼',

  historyTitleCurrency: '貨幣交易記錄',
  historyTitleToken: '代幣交易記錄',
  historyTitleRedemption: '兌換記錄',
  historyPageIndicator: '第 {current} / {total} 頁',
  historyEmpty: '暫無記錄',
  historyPrevBtn: '上一頁',
  historyNextBtn: '下一頁',

  redeemCodeModalTitle: '輸入兌換碼',
  redeemCodeLabel: '兌換碼',
  redeemCodePlaceholder: '請輸入 16 位兌換碼',
  redeemSuccess: '你已成功兌換「{product}」',
  redeemInvalid: '兌換碼無效',
  redeemAlreadyUsed: '此兌換碼已被使用',
  redeemExpired: '此兌換碼已過期',
} as const;
