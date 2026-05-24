/**
 * zh-TW localization strings for the administration package.
 * All user-facing text in Traditional Chinese.
 */

import { DomainErrorCategory } from '@ltdjms/shared';

export const ZhTwStrings = {
  // ============================================================
  // Admin Panel
  // ============================================================
  adminPanelTitle: '管理面板',
  adminPanelDescription: '選擇要管理的功能',
  adminPanelFooter: '管理面板 | 僅限管理員使用',

  // Button labels (9 features)
  adminPanelBtnBalance: '貨幣管理',
  adminPanelBtnToken: '代幣管理',
  adminPanelBtnGame: '遊戲設定',
  adminPanelBtnProduct: '產品／兌換碼',
  adminPanelBtnAIChannel: 'AI 頻道設定',
  adminPanelBtnAIAgent: 'AI Agent 設定',
  adminPanelBtnDispatch: '派單售後設定',
  adminPanelBtnEscortPrice: '護航定價',
  adminPanelBtnEscortCatalog: '護航目錄',

  // ============================================================
  // Permissions, Session, Errors
  // ============================================================
  permissionDenied: '你沒有執行此操作的權限',
  permissionAdminRequired: '此操作需要管理員權限',
  sessionExpired: '面板已過期，請重新執行指令',
  unexpectedError: '發生未預期的錯誤，請聯絡管理員',
  actionTooFrequent: '操作過於頻繁，請稍後再試',
  amountExceedsRange: '金額超出允許範圍',

  // ============================================================
  // Currency Management
  // ============================================================
  balanceSelectMember: '請選擇要查看餘額的成員',
  balanceTitle: '貨幣餘額',
  balanceDisplay: '目前餘額：**{balance}** {currencyIcon}',
  balanceAdjustAdd: '增加',
  balanceAdjustDeduct: '扣除',
  balanceAdjustSet: '設定',
  balanceModalTitleAdd: '增加貨幣',
  balanceModalTitleDeduct: '扣除貨幣',
  balanceModalTitleSet: '設定貨幣',
  balanceModalAmountLabel: '金額',
  balanceModalReasonLabel: '原因',
  balanceModalAmountPlaceholder: '請輸入金額（正整數）',
  balanceModalReasonPlaceholder: '請輸入調整原因（1-256字）',
  balanceSuccessAdjust: '調整成功！\n調整前：{before}\n調整後：{after}',
  balanceErrorPrefix: '調整失敗：',
  balanceSuccessAdd:
    '成功增加 {amount} {currencyIcon}\n調整前：{before} → 調整後：{after}\n原因：{reason}',
  balanceSuccessDeduct:
    '成功扣除 {amount} {currencyIcon}\n調整前：{before} → 調整後：{after}\n原因：{reason}',
  balanceSuccessSet:
    '成功設定為 {amount} {currencyIcon}\n調整前：{before} → 調整後：{after}\n原因：{reason}',
  balanceInsufficient: '目標用戶餘額不足，當前餘額：{balance}',

  // ============================================================
  // Token Management
  // ============================================================
  tokenSelectMember: '請選擇要查看代幣的成員',
  tokenTitle: '遊戲代幣',
  tokenDisplay: '目前代幣：**{tokens}** 個',
  tokenModalTitleAdd: '增加代幣',
  tokenModalTitleDeduct: '扣除代幣',
  tokenModalTitleSet: '設定代幣',
  tokenModalAmountLabel: '數量',
  tokenModalReasonLabel: '原因',
  tokenModalAmountPlaceholder: '請輸入數量（非負整數）',
  tokenModalReasonPlaceholder: '請輸入調整原因（1-256字）',
  tokenSuccessAdjust: '調整成功！\n調整前：{before}\n調整後：{after}',
  tokenErrorPrefix: '調整失敗：',
  tokenSuccessAdd: '成功增加 {amount} 個代幣\n調整前：{before} → 調整後：{after}\n原因：{reason}',
  tokenSuccessDeduct:
    '成功扣除 {amount} 個代幣\n調整前：{before} → 調整後：{after}\n原因：{reason}',
  tokenSuccessSet: '成功設定為 {amount} 個代幣\n調整前：{before} → 調整後：{after}\n原因：{reason}',
  tokenInsufficient: '目標用戶代幣不足，當前代幣：{tokens}',

  // ============================================================
  // Game Settings
  // ============================================================
  gameSelectTitle: '請選擇要設定的遊戲',
  gameDiceGame1: '骰子遊戲 1',
  gameDiceGame2: '骰子遊戲 2',
  gameDice1Title: '骰子遊戲 1 設定',
  gameDice2Title: '骰子遊戲 2 設定',
  gameDice1Fields: '代幣範圍：{min} ~ {max}\n每骰獎勵：{reward}',
  gameDice2Fields:
    '代幣範圍：{min} ~ {max}\n順子倍率：{straight}\n基本倍率：{base}\n三條低獎：{lowTriple}\n三條高獎：{highTriple}',
  gameSaveSuccess: '遊戲設定已成功儲存',
  gameSaveFailed: '遊戲設定儲存失敗：{reason}',
  gameEditBtn: '編輯設定',
  gameModalTitleDice1: '編輯骰子遊戲 1 設定',
  gameModalTitleDice2: '編輯骰子遊戲 2 設定',
  gameModalMin: '最低代幣',
  gameModalMax: '最高代幣',
  gameModalReward: '每骰獎勵代幣',
  gameModalStraightMul: '順子倍率',
  gameModalBaseMul: '基本倍率',
  gameModalTripleLow: '三條低獎倍率',
  gameModalTripleHigh: '三條高獎倍率',
  gameValidationMinMax: '最高代幣必須大於最低代幣',
  gameValidationPositive: '所有數值必須為正數',

  // ============================================================
  // Product Management
  // ============================================================
  productListTitle: '產品列表',
  productListEmpty: '目前沒有任何產品',
  productPageIndicator: '第 {current} / {total} 頁，共 {count} 項',
  productDetailTitle: '{name} 產品詳情',
  productDetailInfo: '價格：{price}\n庫存：{stock}\n描述：{description}',
  productCreateBtn: '新增產品',
  productEditBtn: '編輯',
  productDeleteBtn: '刪除',
  productFiatPriceBtn: '法幣價格',
  productGenerateCodesBtn: '生成兌換碼',
  productBackBtn: '返回',
  productConfirmDelete: '確定要刪除「{name}」嗎？\n此操作無法復原。',
  productDeleted: '產品「{name}」已刪除',
  productCreated: '產品「{name}」已建立',
  productUpdated: '產品「{name}」已更新',
  productCodesGenerated: '已生成 {count} 個兌換碼',
  productCodesTitle: '{name} 兌換碼列表',
  productCodeMasked: '{code}（{status}）',
  productCodeAvailable: '可用',
  productCodeRedeemed: '已使用',
  productCodeExpired: '已過期',
  productFiatPriceSet: '法幣價格已設定為 NT${price}',

  // Product Modal
  productCreateModalTitle: '新增產品',
  productEditModalTitle: '編輯產品',
  productModalName: '產品名稱',
  productModalNamePlaceholder: '請輸入產品名稱',
  productModalDesc: '產品描述',
  productModalDescPlaceholder: '請輸入產品描述（選填）',
  productModalPrice: '貨幣價格',
  productModalPricePlaceholder: '請輸入貨幣價格（選填）',
  productModalStock: '庫存',
  productModalStockPlaceholder: '請輸入庫存數量',
  productModalImageUrl: '圖片網址',
  productModalImageUrlPlaceholder: '請輸入圖片網址（選填）',
  productModalFiatPrice: '法幣價格 (TWD)',
  productModalFiatPricePlaceholder: '請輸入新台幣價格',

  // Generate Codes Modal
  generateCodesModalTitle: '生成兌換碼',
  generateCodesCountLabel: '生成數量',
  generateCodesCountPlaceholder: '請輸入數量（1-100）',
  generateCodesNoteLabel: '備註',
  generateCodesNotePlaceholder: '請輸入備註（選填）',
  generateCodesDaysLabel: '有效天數',
  generateCodesDaysPlaceholder: '請輸入有效天數（留空為永久有效）',

  // ============================================================
  // AI Channel Config
  // ============================================================
  aiChannelTitle: 'AI 頻道設定',
  aiChannelList: '已允許的頻道：\n{channels}\n已允許的分類：\n{categories}',
  aiChannelEmpty: '尚未設定任何允許的頻道或分類',
  aiChannelAddBtn: '新增頻道',
  aiChannelRemoveBtn: '移除頻道',
  aiCategoryAddBtn: '新增分類',
  aiCategoryRemoveBtn: '移除分類',
  aiChannelAdded: '已新增頻道 {channel}',
  aiChannelRemoved: '已移除頻道 {channel}',
  aiCategoryAdded: '已新增分類 {category}',
  aiCategoryRemoved: '已移除分類 {category}',
  aiChannelDuplicate: '該頻道已在白名單中',
  aiChannelNotFound: '該頻道不在白名單中',
  aiCategoryDuplicate: '該分類已在白名單中',
  aiCategoryNotFound: '該分類不在白名單中',
  aiChannelSelectPlaceholder: '請選擇要新增的頻道',
  aiCategorySelectPlaceholder: '請選擇要新增的分類',
  aiChannelRemoveSelectPlaceholder: '請選擇要移除的頻道',
  aiCategoryRemoveSelectPlaceholder: '請選擇要移除的分類',

  // ============================================================
  // AI Agent Config
  // ============================================================
  aiAgentTitle: 'AI Agent 設定',
  aiAgentList: '已啟用 Agent 的頻道：\n{channels}',
  aiAgentEmpty: '尚未啟用任何頻道的 Agent 模式',
  aiAgentEnableBtn: '啟用 Agent',
  aiAgentDisableBtn: '停用 Agent',
  aiAgentRemoveBtn: '移除設定',
  aiAgentSelectChannel: '請選擇要啟用 Agent 的頻道',
  aiAgentSelectDisableChannel: '請選擇要停用 Agent 的頻道',
  aiAgentSelectRemoveChannel: '請選擇要移除設定的頻道',
  aiAgentEnabled: '已啟用頻道 {channel} 的 Agent 模式',
  aiAgentDisabled: '已停用頻道 {channel} 的 Agent 模式',
  aiAgentRemoved: '已移除頻道 {channel} 的 Agent 設定',
  aiAgentModeLabel: 'Agent 模式',

  // ============================================================
  // Dispatch After-Sales Config
  // ============================================================
  dispatchTitle: '售後人員設定',
  dispatchStaffList: '目前售後人員：\n{staffs}',
  dispatchStaffEmpty: '尚未設定任何售後人員',
  dispatchAddBtn: '新增售後人員',
  dispatchRemoveBtn: '移除售後人員',
  dispatchSelectMember: '請選擇要新增的售後人員',
  dispatchSelectRemove: '請選擇要移除的售後人員',
  dispatchStaffAdded: '已新增售後人員 {member}',
  dispatchStaffRemoved: '已移除售後人員 {member}',
  dispatchStaffDuplicate: '該成員已在售後名單中',
  dispatchStaffNotFound: '該成員不在售後名單中',

  // ============================================================
  // Escort Pricing
  // ============================================================
  escortPricingTitle: '護航定價',
  escortPricingList: '護航選項價格列表：\n\n{items}',
  escortPricingItem: '**{name}**\n全域預設：NT${default}\n{guildOverride}',
  escortPricingGuildOverride: 'Guild 覆寫：NT${price}',
  escortPricingNoOverride: '無 guild 覆寫（使用全域預設）',
  escortPricingEditBtn: '編輯價格',
  escortPricingResetBtn: '重設價格',
  escortPricingEditTitle: '編輯護航價格',
  escortPricingEditLabel: '價格 (TWD)',
  escortPricingEditPlaceholder: '請輸入新價格',
  escortPricingResetConfirm: '確定要重設「{name}」的價格為全域預設值嗎？',
  escortPricingUpdated: '已更新「{name}」的價格為 NT${price}',
  escortPricingResetDone: '已重設「{name}」的價格為全域預設值 NT${price}',

  // ============================================================
  // Escort Catalog CRUD
  // ============================================================
  escortCatalogTitle: '護航目錄管理',
  escortCatalogList: '護航目錄項目：\n\n{items}',
  escortCatalogItem: '**{name}**（{category}）\n基礎價格：NT${price}\n{description}',
  escortCatalogEmpty: '目前沒有任何護航目錄項目',
  escortCatalogCreateBtn: '新增項目',
  escortCatalogEditBtn: '編輯',
  escortCatalogDeleteBtn: '刪除',
  escortCatalogCreateTitle: '新增護航目錄項目',
  escortCatalogEditTitle: '編輯護航目錄項目',
  escortCatalogModalName: '名稱',
  escortCatalogModalNamePlaceholder: '請輸入項目名稱',
  escortCatalogModalDesc: '描述',
  escortCatalogModalDescPlaceholder: '請輸入項目描述',
  escortCatalogModalPrice: '基礎價格',
  escortCatalogModalPricePlaceholder: '請輸入基礎價格 (TWD)',
  escortCatalogModalCategory: '類別',
  escortCatalogModalCategoryPlaceholder: '請輸入類別',
  escortCatalogConfirmDelete: '確定要刪除「{name}」嗎？',
  escortCatalogDeleteBlocked: '無法刪除「{name}」，因為以下 guild 正在使用此項目：\n{guilds}',
  escortCatalogDeleted: '已刪除目錄項目「{name}」',
  escortCatalogCreated: '已新增目錄項目「{name}」',
  escortCatalogUpdated: '已更新目錄項目「{name}」',

  // ============================================================
  // User Panel
  // ============================================================
  userPanelTitle: '用戶面板',
  userPanelBalance: '貨幣餘額：**{balance}** {currencyIcon}',
  userPanelTokens: '遊戲代幣：**{tokens}** 個',
  userPanelCurrencyName: '貨幣名稱：{name}',

  userPanelBtnCurrencyHistory: '貨幣記錄',
  userPanelBtnTokenHistory: '代幣記錄',
  userPanelBtnRedemptionHistory: '兌換記錄',
  userPanelBtnRedeemCode: '輸入兌換碼',

  // ============================================================
  // Transaction History (shared between admin and user)
  // ============================================================
  historyTitleCurrency: '貨幣交易記錄',
  historyTitleToken: '代幣交易記錄',
  historyTitleRedemption: '兌換記錄',
  historyPageIndicator: '第 {current} / {total} 頁',
  historyEmpty: '暫無記錄',
  historyPrevBtn: '上一頁',
  historyNextBtn: '下一頁',
  historyBackBtn: '返回',
  historyItemCurrency: '{time}\n{amount} {currencyIcon} — {type}\n{note}',
  historyItemToken: '{time}\n{amount} 個 — {type}\n{note}',
  historyItemRedemption: '{time}\n{product}\n兌換碼：{code}',

  // ============================================================
  // Redemption Code Input
  // ============================================================
  redeemCodeModalTitle: '輸入兌換碼',
  redeemCodeLabel: '兌換碼',
  redeemCodePlaceholder: '請輸入 16 位兌換碼',
  redeemSuccess: '你已成功兌換「{product}」',
  redeemInvalid: '兌換碼無效',
  redeemAlreadyUsed: '此兌換碼已被使用',
  redeemExpired: '此兌換碼已過期',
  redeemInvalidated: '此兌換碼已失效',

  // ============================================================
  // DomainError → zh-TW Error Message Mapping
  // ============================================================
  errorMapping: {
    [DomainErrorCategory.INVALID_INPUT]: '輸入資料無效',
    [DomainErrorCategory.INSUFFICIENT_BALANCE]: '餘額不足',
    [DomainErrorCategory.INSUFFICIENT_TOKENS]: '代幣不足',
    [DomainErrorCategory.PERSISTENCE_FAILURE]: '資料操作失敗，請稍後再試',
    [DomainErrorCategory.UNEXPECTED_FAILURE]: '發生未預期的錯誤，請聯絡管理員',
    [DomainErrorCategory.DISCORD_INTERACTION_TIMEOUT]: '互動已超時，請重新操作',
    [DomainErrorCategory.DISCORD_HOOK_EXPIRED]: '互動已過期，請重新操作',
    [DomainErrorCategory.DISCORD_UNKNOWN_MESSAGE]: '訊息不存在',
    [DomainErrorCategory.DISCORD_RATE_LIMITED]: '操作過於頻繁，請稍後再試',
    [DomainErrorCategory.DISCORD_MISSING_PERMISSIONS]: '機器人缺少執行此操作的權限',
    [DomainErrorCategory.DISCORD_INVALID_COMPONENT_ID]: '按鈕已失效，請重新操作',
    [DomainErrorCategory.AI_SERVICE_TIMEOUT]: 'AI 服務連線逾時，請稍後再試',
    [DomainErrorCategory.AI_SERVICE_AUTH_FAILED]: 'AI 服務認證失敗',
    [DomainErrorCategory.AI_SERVICE_RATE_LIMITED]: 'AI 服務請求過於頻繁，請稍後再試',
    [DomainErrorCategory.AI_SERVICE_UNAVAILABLE]: 'AI 服務暫時無法使用',
    [DomainErrorCategory.AI_RESPONSE_EMPTY]: 'AI 回覆為空',
    [DomainErrorCategory.AI_RESPONSE_INVALID]: 'AI 回覆格式無效',
    [DomainErrorCategory.PROMPT_DIR_NOT_FOUND]: '提示詞目錄不存在',
    [DomainErrorCategory.PROMPT_FILE_TOO_LARGE]: '提示詞檔案過大',
    [DomainErrorCategory.PROMPT_READ_FAILED]: '讀取提示詞失敗',
    [DomainErrorCategory.PROMPT_INVALID_ENCODING]: '提示詞編碼無效',
    [DomainErrorCategory.PROMPT_LOAD_FAILED]: '載入提示詞失敗',
    [DomainErrorCategory.CHANNEL_NOT_ALLOWED]: '該頻道未在白名單中',
    [DomainErrorCategory.DUPLICATE_CHANNEL]: '該頻道已在白名單中',
    [DomainErrorCategory.INSUFFICIENT_PERMISSIONS]: '你沒有執行此操作的權限',
    [DomainErrorCategory.CHANNEL_NOT_FOUND]: '找不到指定的頻道',
    [DomainErrorCategory.DUPLICATE_CATEGORY]: '該分類已在白名單中',
    [DomainErrorCategory.CATEGORY_NOT_FOUND]: '找不到指定的分類',
    [DomainErrorCategory.REDEEM_CODE_USED]: '此兌換碼已被使用',
    [DomainErrorCategory.REDEEM_CODE_EXPIRED]: '此兌換碼已過期',
    [DomainErrorCategory.REDEEM_CODE_INVALID]: '兌換碼無效',
  },

  // ============================================================
  // Discord API Common Error Codes
  // ============================================================
  discordError10062: '互動已過期，請重新操作',
  discordError50001: '機器人缺少存取權限',
  discordError50007: '無法發送訊息給此用戶',
  discordError30046: '已經有正在進行的互動',
} as const;

export type ZhTwStringsType = typeof ZhTwStrings;

/**
 * Type-level exhaustiveness check: ensures every DomainErrorCategory value
 * has a corresponding entry in errorMapping.
 * If a new category is added to DomainErrorCategory without adding its zh-TW
 * message, this line will cause a compile-time error.
 */

const _exhaustiveCategoryCheck: DomainErrorCategory extends keyof typeof ZhTwStrings.errorMapping
  ? true
  : false = true;
