/**
 * zh-TW localization strings for economy module.
 * All user-facing text in Traditional Chinese.
 */

export const DiceGameMessages = {
  // ============================================================
  // Dice Game 1
  // ============================================================
  GAME_1_TITLE: '🎲 骰子遊戲 1',
  GAME_1_DESCRIPTION: '擲 {count} 顆骰子，總獎勵 {reward} 貨幣',
  GAME_1_RESULT:
    '**骰子結果：** {dice}\n**總和：** {sum}\n**獎勵：** {reward} 貨幣',

  // ============================================================
  // Dice Game 2
  // ============================================================
  GAME_2_TITLE: '🎲 骰子遊戲 2',
  GAME_2_DESCRIPTION: '每 1 代幣擲 3 顆骰子，分析順子、三條與一般骰子',
  GAME_2_RESULT:
    '**骰子結果：** {dice}\n\n**順子區段：** {straightSegments}\n**三條區段：** {tripleSegments}\n\n**順子獎勵：** {straightReward}\n**三條獎勵：** {tripleReward}\n**一般獎勵：** {baseReward}\n**總獎勵：** {totalReward} 貨幣\n\n**餘額變動：** {previousBalance} → {newBalance} 貨幣',

  // ============================================================
  // Balance
  // ============================================================
  BALANCE_TITLE: '貨幣餘額',
  BALANCE_DISPLAY: '目前餘額：**{balance}** {currencyIcon}\n貨幣名稱：{currencyName}',
  BALANCE_FETCH_FAILED: '無法取得餘額資訊，請稍後再試',

  // ============================================================
  // Currency Config
  // ============================================================
  CURRENCY_CONFIG_SUCCESS: '貨幣設定已成功更新\n名稱：{name}\n圖標：{icon}',
  CURRENCY_CONFIG_FAILED: '貨幣設定更新失敗：{reason}',

  // ============================================================
  // Game Token Adjust
  // ============================================================
  TOKEN_ADJUST_TITLE: '代幣調整',
  TOKEN_ADJUST_SUCCESS:
    '成功調整代幣\n調整前：{before} → 調整後：{after}\n調整量：{amount}',
  TOKEN_ADJUST_FAILED: '代幣調整失敗：{reason}',
  TOKEN_INSUFFICIENT: '代幣不足',
  TOKEN_CURRENT_BALANCE: '目前代幣：**{tokens}** 個',

  // ============================================================
  // Dice Config
  // ============================================================
  DICE_CONFIG_1_TITLE: '骰子遊戲 1 設定',
  DICE_CONFIG_2_TITLE: '骰子遊戲 2 設定',
  DICE_CONFIG_SUCCESS: '遊戲設定已成功儲存',
  DICE_CONFIG_FAILED: '遊戲設定儲存失敗：{reason}',
  DICE_CONFIG_1_DISPLAY:
    '代幣範圍：{min} ~ {max}\n每骰獎勵：{reward}',
  DICE_CONFIG_2_DISPLAY:
    '代幣範圍：{min} ~ {max}\n順子倍率：{straightMul}\n基本倍率：{baseMul}\n三條低獎：{tripleLow}\n三條高獎：{tripleHigh}',

  // ============================================================
  // Common / Errors
  // ============================================================
  UNEXPECTED_ERROR: '發生未預期的錯誤，請聯絡管理員',
  INVALID_TOKEN_COUNT: '代幣數量無效',
  TOKEN_COUNT_TOO_LOW: '代幣數量低於最低限制（{min}）',
  TOKEN_COUNT_TOO_HIGH: '代幣數量超過最高限制（{max}）',
  INVALID_OPTION: '指令參數無效',
} as const;
