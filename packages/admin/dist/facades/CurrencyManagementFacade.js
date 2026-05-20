import { Err, DomainError } from '@ltdjms/shared';
import { CurrencyTransactionSource, } from '@ltdjms/economy';
/**
 * Adjustment mode for balance operations.
 */
export var BalanceAdjustMode;
(function (BalanceAdjustMode) {
    BalanceAdjustMode["ADD"] = "ADD";
    BalanceAdjustMode["DEDUCT"] = "DEDUCT";
    BalanceAdjustMode["SET"] = "SET";
})(BalanceAdjustMode || (BalanceAdjustMode = {}));
/**
 * Facade that aggregates currency management operations.
 * Wraps BalanceService, BalanceAdjustmentService, and CurrencyConfigService.
 * Matches Java CurrencyManagementFacade.
 */
export class CurrencyManagementFacade {
    balanceService;
    balanceAdjustmentService;
    currencyConfigService;
    constructor(balanceService, balanceAdjustmentService, currencyConfigService) {
        this.balanceService = balanceService;
        this.balanceAdjustmentService = balanceAdjustmentService;
        this.currencyConfigService = currencyConfigService;
    }
    /**
     * Gets the currency configuration for a guild.
     */
    async getConfig(guildId) {
        return this.currencyConfigService.tryGetConfig(guildId);
    }
    /**
     * Gets the balance view for a member in a guild.
     */
    async getBalance(guildId, userId) {
        return this.balanceService.tryGetBalance(guildId, userId);
    }
    /**
     * Adjusts a member's balance by adding the specified positive amount.
     */
    async adjustBalance(guildId, userId, amount, reason, actorId) {
        const validation = this.validateAdjustmentAmount(amount, '增加');
        if (validation)
            return validation;
        return this.balanceAdjustmentService.tryAdjustBalance(guildId, userId, amount, CurrencyTransactionSource.ADMIN_ADJUSTMENT, `管理員 ${actorId}：${reason}`);
    }
    /**
     * Deducts from a member's balance.
     */
    async deductBalance(guildId, userId, amount, reason, actorId) {
        const validation = this.validateAdjustmentAmount(amount, '扣除');
        if (validation)
            return validation;
        return this.balanceAdjustmentService.tryAdjustBalance(guildId, userId, -amount, CurrencyTransactionSource.ADMIN_ADJUSTMENT, `管理員 ${actorId}：${reason}`);
    }
    /**
     * Sets a member's balance to a specific target value.
     */
    async setBalance(guildId, userId, amount, reason, actorId) {
        if (!Number.isFinite(amount) || amount < 0) {
            return new Err(DomainError.invalidInput('設定金額必須為非負整數'));
        }
        return this.balanceAdjustmentService.tryAdjustBalanceTo(guildId, userId, amount, CurrencyTransactionSource.ADMIN_ADJUSTMENT, `管理員 ${actorId}：${reason}`);
    }
    validateAdjustmentAmount(amount, _operationName) {
        if (!Number.isFinite(amount) || amount <= 0) {
            return new Err(DomainError.invalidInput('調整金額必須為正整數'));
        }
        if (amount > Number.MAX_SAFE_INTEGER) {
            return new Err(DomainError.invalidInput('金額超出允許範圍'));
        }
        return null;
    }
}
//# sourceMappingURL=CurrencyManagementFacade.js.map