import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Builder for admin panel modal configurations.
 * Provides structured data that Discord modal builders consume.
 * Matches Java AdminPanelModalFactory.
 *
 * RESPONSIBILITY: This factory handles shared/generic admin panel modals
 * (balance, tokens, games, escort pricing/catalog).
 * Product-specific modals (create/edit product, generate codes) are in
 * AdminProductPanelModalFactory under panel/admin/product/.
 *
 * @see AdminProductPanelModalFactory — product-specific modals
 */
export class AdminPanelModalFactory {
    /**
     * Builds a balance adjustment modal.
     */
    buildBalanceAdjustModal(mode) {
        const titles = {
            add: ZhTwStrings.balanceModalTitleAdd,
            deduct: ZhTwStrings.balanceModalTitleDeduct,
            set: ZhTwStrings.balanceModalTitleSet,
        };
        return {
            title: titles[mode] ?? '調整貨幣',
            fields: [
                {
                    label: ZhTwStrings.balanceModalAmountLabel,
                    placeholder: ZhTwStrings.balanceModalAmountPlaceholder,
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.balanceModalReasonLabel,
                    placeholder: ZhTwStrings.balanceModalReasonPlaceholder,
                    minLength: 1,
                    maxLength: 256,
                    required: true,
                },
            ],
        };
    }
    /**
     * Builds a token adjustment modal.
     */
    buildTokenAdjustModal(mode) {
        const titles = {
            add: ZhTwStrings.tokenModalTitleAdd,
            deduct: ZhTwStrings.tokenModalTitleDeduct,
            set: ZhTwStrings.tokenModalTitleSet,
        };
        return {
            title: titles[mode] ?? '調整代幣',
            fields: [
                {
                    label: ZhTwStrings.tokenModalAmountLabel,
                    placeholder: ZhTwStrings.tokenModalAmountPlaceholder,
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.tokenModalReasonLabel,
                    placeholder: ZhTwStrings.tokenModalReasonPlaceholder,
                    minLength: 1,
                    maxLength: 256,
                    required: true,
                },
            ],
        };
    }
    /**
     * Builds a dice game 1 settings modal.
     */
    buildDiceGame1SettingsModal(currentConfig) {
        return {
            title: ZhTwStrings.gameModalTitleDice1,
            fields: [
                {
                    label: ZhTwStrings.gameModalMin,
                    value: String(currentConfig.minTokensPerPlay),
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalMax,
                    value: String(currentConfig.maxTokensPerPlay),
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalReward,
                    value: String(currentConfig.rewardPerDiceValue),
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
            ],
        };
    }
    /**
     * Builds a dice game 2 settings modal with all multipliers.
     */
    buildDiceGame2SettingsModal(currentConfig) {
        return {
            title: ZhTwStrings.gameModalTitleDice2,
            fields: [
                {
                    label: ZhTwStrings.gameModalMin,
                    value: String(currentConfig.minTokensPerPlay),
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalMax,
                    value: String(currentConfig.maxTokensPerPlay),
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalStraightMul,
                    value: String(currentConfig.straightMultiplier),
                    minLength: 1,
                    maxLength: 10,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalBaseMul,
                    value: String(currentConfig.baseMultiplier),
                    minLength: 1,
                    maxLength: 10,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalTripleLow,
                    value: String(currentConfig.tripleLowBonus),
                    minLength: 1,
                    maxLength: 10,
                    required: true,
                },
                {
                    label: ZhTwStrings.gameModalTripleHigh,
                    value: String(currentConfig.tripleHighBonus),
                    minLength: 1,
                    maxLength: 10,
                    required: true,
                },
            ],
        };
    }
    /**
     * Builds a product creation modal.
     */
    buildProductCreateModal() {
        return {
            title: ZhTwStrings.productCreateModalTitle,
            fields: [
                { label: ZhTwStrings.productModalName, placeholder: ZhTwStrings.productModalNamePlaceholder, minLength: 1, maxLength: 100, required: true },
                { label: ZhTwStrings.productModalDesc, placeholder: ZhTwStrings.productModalDescPlaceholder, minLength: 0, maxLength: 1000, required: false },
                { label: ZhTwStrings.productModalPrice, placeholder: ZhTwStrings.productModalPricePlaceholder, minLength: 0, maxLength: 20, required: false },
                { label: ZhTwStrings.productModalFiatPrice, placeholder: ZhTwStrings.productModalFiatPricePlaceholder, minLength: 0, maxLength: 20, required: false },
            ],
        };
    }
    /**
     * Builds a generate codes modal.
     */
    buildGenerateCodesModal() {
        return {
            title: ZhTwStrings.generateCodesModalTitle,
            fields: [
                { label: ZhTwStrings.generateCodesCountLabel, placeholder: ZhTwStrings.generateCodesCountPlaceholder, minLength: 1, maxLength: 3, required: true },
                { label: ZhTwStrings.generateCodesNoteLabel, placeholder: ZhTwStrings.generateCodesNotePlaceholder, minLength: 0, maxLength: 100, required: false },
            ],
        };
    }
    /**
     * Builds an escort pricing edit modal.
     */
    buildEscortPricingEditModal(optionName, _globalPrice, currentOverride) {
        return {
            title: `${ZhTwStrings.escortPricingEditTitle} - ${optionName}`,
            fields: [
                {
                    label: ZhTwStrings.escortPricingEditLabel,
                    value: currentOverride != null ? String(currentOverride) : '',
                    placeholder: ZhTwStrings.escortPricingEditPlaceholder,
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
            ],
        };
    }
    /**
     * Builds an escort catalog create/edit modal.
     */
    buildEscortCatalogModal(currentEntry) {
        return {
            title: currentEntry ? ZhTwStrings.escortCatalogEditTitle : ZhTwStrings.escortCatalogCreateTitle,
            fields: [
                {
                    label: ZhTwStrings.escortCatalogModalName,
                    value: currentEntry?.code ?? '',
                    placeholder: ZhTwStrings.escortCatalogModalNamePlaceholder,
                    minLength: 1,
                    maxLength: 100,
                    required: true,
                },
                {
                    label: ZhTwStrings.escortCatalogModalDesc,
                    value: currentEntry?.mapScope ?? '',
                    placeholder: ZhTwStrings.escortCatalogModalDescPlaceholder,
                    minLength: 0,
                    maxLength: 500,
                    required: false,
                },
                {
                    label: ZhTwStrings.escortCatalogModalPrice,
                    value: currentEntry?.priceTwd != null ? String(currentEntry.priceTwd) : '',
                    placeholder: ZhTwStrings.escortCatalogModalPricePlaceholder,
                    minLength: 1,
                    maxLength: 20,
                    required: true,
                },
                {
                    label: ZhTwStrings.escortCatalogModalCategory,
                    value: currentEntry?.type ?? '',
                    placeholder: ZhTwStrings.escortCatalogModalCategoryPlaceholder,
                    minLength: 1,
                    maxLength: 50,
                    required: true,
                },
            ],
        };
    }
}
//# sourceMappingURL=AdminPanelModalFactory.js.map