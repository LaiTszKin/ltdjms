export const DiceGameMessages = {
  // Game 1
  GAME_1_TITLE: '骰子遊戲結果',
  GAME_1_RESULT:
    '骰子結果：{dice}\n\n總獎勵：{icon} {reward} {name}\n新餘額：{icon} {newBalance} {name}',

  // Game 2
  GAME_2_TITLE: '骰子遊戲2結果',
  GAME_2_DICE_RESULT: '骰子結果：{dice}',
  GAME_2_STRAIGHT_REWARD: '順子：{icon} {reward} {name}',
  GAME_2_TRIPLE_REWARD: '三條：{icon} {reward} {name}（{count} 組）',
  GAME_2_BASE_REWARD: '基礎：{icon} {reward} {name}',
  GAME_2_TOTAL_REWARD: '**總獎勵：** {icon} {reward} {name}',
  GAME_2_NEW_BALANCE: '**新餘額：** {icon} {balance} {name}',

  // Error messages
  MISSING_TOKENS_ERROR: '請輸入本局要投入的遊戲代幣數量！\n必須介於 {min} ~ {max} 代幣之間',
  TOKEN_RANGE_ERROR: '代幣投入數量超出範圍！\n您輸入的數量：{input}\n允許範圍：{min} ~ {max} 代幣',
  TOKEN_INSUFFICIENT_ERROR: '遊戲代幣不足！\n需要：{required} 代幣\n目前餘額：{current} 代幣',

  // Non-game constants (unchanged)
  BALANCE_TITLE: '貨幣餘額',
  BALANCE_DISPLAY: '目前餘額：**{balance}** {currencyIcon}\n貨幣名稱：{currencyName}',
  BALANCE_FETCH_FAILED: '無法取得餘額資訊，請稍後再試',
  CURRENCY_CONFIG_SUCCESS: '貨幣設定已成功更新\n名稱：{name}\n圖標：{icon}',
  CURRENCY_CONFIG_FAILED: '貨幣設定更新失敗：{reason}',
  TOKEN_ADJUST_TITLE: '代幣調整',
  TOKEN_ADJUST_SUCCESS: '成功調整代幣\n調整前：{before} → 調整後：{after}\n調整量：{amount}',
  TOKEN_ADJUST_FAILED: '代幣調整失敗：{reason}',
  TOKEN_CURRENT_BALANCE: '目前代幣：**{tokens}** 個',
  DICE_CONFIG_1_TITLE: '骰子遊戲 1 設定',
  DICE_CONFIG_2_TITLE: '骰子遊戲 2 設定',
  DICE_CONFIG_SUCCESS: '遊戲設定已成功儲存',
  DICE_CONFIG_FAILED: '遊戲設定儲存失敗：{reason}',
  DICE_CONFIG_1_DISPLAY: '代幣範圍：{min} ~ {max}\n每骰獎勵：{reward}',
  DICE_CONFIG_2_DISPLAY:
    '代幣範圍：{min} ~ {max}\n順子倍率：{straightMul}\n基本倍率：{baseMul}\n三條低獎：{tripleLow}\n三條高獎：{tripleHigh}',
  UNEXPECTED_ERROR: '發生未預期的錯誤，請聯絡管理員',
  INVALID_OPTION: '指令參數無效',
} as const;
