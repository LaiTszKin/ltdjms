package ltdjms.discord.panel.commands;

import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

final class AdminPanelModalFactory {

  private AdminPanelModalFactory() {}

  static Modal createBalanceAdjustModal(
      long userId, String mode, String userMention, String modeLabel) {
    String modalTitle = String.format("%s - %s", modeLabel, userMention);
    if (modalTitle.length() > 45) {
      modalTitle = modeLabel;
    }

    TextInput amountInput =
        TextInput.create("amount", "金額", TextInputStyle.SHORT)
            .setPlaceholder(mode.equals("adjust") ? "輸入目標餘額" : "輸入調整金額")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(
            AdminPanelButtonHandler.MODAL_BALANCE_ADJUST + ":" + userId + ":" + mode, modalTitle)
        .addComponents(ActionRow.of(amountInput))
        .build();
  }

  static Modal createTokenAdjustModal(
      long userId, String mode, String userMention, String modeLabel) {
    String modalTitle = String.format("%s - %s", modeLabel, userMention);
    if (modalTitle.length() > 45) {
      modalTitle = modeLabel;
    }

    TextInput amountInput =
        TextInput.create("amount", "數量", TextInputStyle.SHORT)
            .setPlaceholder(mode.equals("adjust") ? "輸入目標代幣數量" : "輸入調整數量")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(
            AdminPanelButtonHandler.MODAL_TOKEN_ADJUST + ":" + userId + ":" + mode, modalTitle)
        .addComponents(ActionRow.of(amountInput))
        .build();
  }

  static Modal createMembershipSpendModal(
      long userId, String mode, String userMention, String modeLabel) {
    String modalTitle = String.format("%s - %s", modeLabel, userMention);
    if (modalTitle.length() > 45) {
      modalTitle = modeLabel;
    }

    TextInput amountInput =
        TextInput.create("amount", "M 數值", TextInputStyle.SHORT)
            .setPlaceholder(mode.equals("set") ? "輸入目標 M" : "輸入調整 M")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(
            AdminPanelButtonHandler.MODAL_MEMBERSHIP_SPEND + ":" + userId + ":" + mode, modalTitle)
        .addComponents(ActionRow.of(amountInput))
        .build();
  }

  static Modal createGame1TokensModal(DiceGame1Config config) {
    TextInput minInput =
        TextInput.create("min", "最小代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最小可投入代幣數量")
            .setValue(String.valueOf(config.minTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    TextInput maxInput =
        TextInput.create("max", "最大代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最大可投入代幣數量")
            .setValue(String.valueOf(config.maxTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    return Modal.create(AdminPanelButtonHandler.MODAL_GAME_1_TOKENS, "摘星手 - 代幣範圍")
        .addComponents(ActionRow.of(minInput), ActionRow.of(maxInput))
        .build();
  }

  static Modal createGame1RewardModal(DiceGame1Config config) {
    TextInput rewardInput =
        TextInput.create("reward", "單骰獎勵倍率", TextInputStyle.SHORT)
            .setPlaceholder("每點數的獎勵金額")
            .setValue(String.valueOf(config.rewardPerDiceValue()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(AdminPanelButtonHandler.MODAL_GAME_1_REWARD, "摘星手 - 獎勵設定")
        .addComponents(ActionRow.of(rewardInput))
        .build();
  }

  static Modal createGame2TokensModal(DiceGame2Config config) {
    TextInput minInput =
        TextInput.create("min", "最小代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最小可投入代幣數量")
            .setValue(String.valueOf(config.minTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    TextInput maxInput =
        TextInput.create("max", "最大代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最大可投入代幣數量")
            .setValue(String.valueOf(config.maxTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    return Modal.create(AdminPanelButtonHandler.MODAL_GAME_2_TOKENS, "神龍擺尾 - 代幣範圍")
        .addComponents(ActionRow.of(minInput), ActionRow.of(maxInput))
        .build();
  }

  static Modal createGame2MultipliersModal(DiceGame2Config config) {
    TextInput straightInput =
        TextInput.create("straight", "順子倍率", TextInputStyle.SHORT)
            .setPlaceholder("順子獎勵的基礎倍率")
            .setValue(String.valueOf(config.straightMultiplier()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    TextInput baseInput =
        TextInput.create("base", "基礎倍率", TextInputStyle.SHORT)
            .setPlaceholder("非順子非豹子的基礎倍率")
            .setValue(String.valueOf(config.baseMultiplier()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(AdminPanelButtonHandler.MODAL_GAME_2_MULTIPLIERS, "神龍擺尾 - 獎勵倍率")
        .addComponents(ActionRow.of(straightInput), ActionRow.of(baseInput))
        .build();
  }

  static Modal createGame2BonusesModal(DiceGame2Config config) {
    TextInput lowInput =
        TextInput.create("low", "小豹子獎勵", TextInputStyle.SHORT)
            .setPlaceholder("小豹子（總和<10）的獎勵金額")
            .setValue(String.valueOf(config.tripleLowBonus()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    TextInput highInput =
        TextInput.create("high", "大豹子獎勵", TextInputStyle.SHORT)
            .setPlaceholder("大豹子（總和≥10）的獎勵金額")
            .setValue(String.valueOf(config.tripleHighBonus()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(AdminPanelButtonHandler.MODAL_GAME_2_BONUSES, "神龍擺尾 - 豹子獎勵")
        .addComponents(ActionRow.of(lowInput), ActionRow.of(highInput))
        .build();
  }

  // ========== Escort Catalog Modals ==========

  /** Creates a modal for adding a new escort catalog item. */
  static Modal createEscortCatalogCreateModal() {
    TextInput codeInput =
        TextInput.create("escort_cat_code", "選項代碼", TextInputStyle.SHORT)
            .setPlaceholder("例如：CONF_DAM_300W")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(120)
            .build();

    TextInput typeInput =
        TextInput.create("escort_cat_type", "訂單類型", TextInputStyle.SHORT)
            .setPlaceholder("例如：包本單、小時單、指定大紅")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(64)
            .build();

    TextInput levelInput =
        TextInput.create("escort_cat_level", "服務級別", TextInputStyle.SHORT)
            .setPlaceholder("例如：機密護、絕密護、不限")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(64)
            .build();

    TextInput scopeTargetInput =
        TextInput.create("escort_cat_scope_target", "服務範圍 / 目標", TextInputStyle.PARAGRAPH)
            .setPlaceholder("第一行：服務範圍，例如 機密大壩\n第二行：目標說明，例如 300 萬目標")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(500)
            .build();

    TextInput priceInput =
        TextInput.create("escort_cat_price", "價格（TWD）", TextInputStyle.SHORT)
            .setPlaceholder("新台幣整數，例如 500")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(AdminPanelButtonHandler.MODAL_ESCORT_CATALOG_CREATE, "新增護航項目")
        .addComponents(
            ActionRow.of(codeInput),
            ActionRow.of(typeInput),
            ActionRow.of(levelInput),
            ActionRow.of(scopeTargetInput),
            ActionRow.of(priceInput))
        .build();
  }

  /**
   * Creates a modal for editing an existing escort catalog item, pre-filled with current values.
   */
  static Modal createEscortCatalogEditModal(EscortOptionCatalog catalog) {
    String modalId = AdminPanelButtonHandler.MODAL_ESCORT_CATALOG_EDIT + ":" + catalog.code();
    String modalTitle = "編輯護航項目 - " + catalog.code();
    if (modalTitle.length() > 45) {
      modalTitle = "編輯護航項目";
    }

    TextInput codeInput =
        TextInput.create("escort_cat_code", "選項代碼", TextInputStyle.SHORT)
            .setPlaceholder("例如：CONF_DAM_300W")
            .setValue(catalog.code())
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(120)
            .build();

    TextInput typeInput =
        TextInput.create("escort_cat_type", "訂單類型", TextInputStyle.SHORT)
            .setPlaceholder("例如：包本單、小時單、指定大紅")
            .setValue(catalog.type())
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(64)
            .build();

    TextInput levelInput =
        TextInput.create("escort_cat_level", "服務級別", TextInputStyle.SHORT)
            .setPlaceholder("例如：機密護、絕密護、不限")
            .setValue(catalog.level())
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(64)
            .build();

    TextInput scopeTargetInput =
        TextInput.create("escort_cat_scope_target", "服務範圍 / 目標", TextInputStyle.PARAGRAPH)
            .setPlaceholder("第一行：服務範圍\n第二行：目標說明")
            .setValue(catalog.mapScope() + "\n" + catalog.target())
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(500)
            .build();

    TextInput priceInput =
        TextInput.create("escort_cat_price", "價格（TWD）", TextInputStyle.SHORT)
            .setPlaceholder("新台幣整數，例如 500")
            .setValue(String.valueOf(catalog.priceTwd()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    return Modal.create(modalId, modalTitle)
        .addComponents(
            ActionRow.of(codeInput),
            ActionRow.of(typeInput),
            ActionRow.of(levelInput),
            ActionRow.of(scopeTargetInput),
            ActionRow.of(priceInput))
        .build();
  }
}
