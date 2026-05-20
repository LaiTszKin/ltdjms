/**
 * Product domain type matching Java Product record.
 * This is a minimal type used internally by the shop package.
 * The full Product CRUD belongs to the administration domain.
 */
export var RewardType;
(function (RewardType) {
    RewardType["CURRENCY"] = "CURRENCY";
    RewardType["TOKEN"] = "TOKEN";
})(RewardType || (RewardType = {}));
export function hasReward(product) {
    return product.rewardType !== null && product.rewardAmount !== null;
}
export function formatReward(product) {
    if (!hasReward(product))
        return null;
    switch (product.rewardType) {
        case RewardType.CURRENCY:
            return `${product.rewardAmount.toLocaleString()} 货币`;
        case RewardType.TOKEN:
            return `${product.rewardAmount.toLocaleString()} 代币`;
        default:
            return null;
    }
}
export function hasCurrencyPrice(product) {
    return product.currencyPrice !== null && product.currencyPrice > 0;
}
export function formatCurrencyPrice(product) {
    if (!hasCurrencyPrice(product))
        return null;
    return `${product.currencyPrice.toLocaleString()} 货币`;
}
export function hasFiatPriceTwd(product) {
    return product.fiatPriceTwd !== null && product.fiatPriceTwd > 0;
}
export function formatFiatPriceTwd(product) {
    if (!hasFiatPriceTwd(product))
        return null;
    return `NT$${product.fiatPriceTwd.toLocaleString()}`;
}
export function isFiatOnly(product) {
    return hasFiatPriceTwd(product) && !hasCurrencyPrice(product);
}
export function shouldAutoCreateEscortOrder(product) {
    return product.autoCreateEscortOrder
        && product.escortOptionCode !== null
        && product.escortOptionCode.trim().length > 0;
}
/**
 * Creates a Product instance. Note that full Product CRUD belongs to the
 * administration module and should be moved there when the admin package is
 * established. This factory is provided here for internal shop domain use (P2-20).
 */
export function createProduct(guildId, name, description, rewardType, rewardAmount, currencyPrice, fiatPriceTwd, autoCreateEscortOrder = false, escortOptionCode = null) {
    const now = new Date();
    return {
        id: null,
        guildId,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        autoCreateEscortOrder,
        escortOptionCode,
        createdAt: now,
        updatedAt: now,
    };
}
//# sourceMappingURL=product-types.js.map