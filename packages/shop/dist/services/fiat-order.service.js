import { ok, err, DomainError } from '@ltdjms/shared';
import { createPending } from '../domain/fiat-order.js';
import { hasFiatPriceTwd, isFiatOnly, formatFiatPriceTwd, } from '../domain/product-types.js';
import pino from 'pino';
export function formatFiatOrderDMMessage(result) {
    const lines = [];
    lines.push('✅ 法幣訂單建立成功！\n');
    lines.push(`**商品：** ${result.product.name}`);
    lines.push(`**訂單編號：** \`${result.orderNumber}\``);
    lines.push(`**超商代碼：** \`${result.paymentNo}\``);
    lines.push(`**金額：** ${formatFiatPriceTwd(result.product) ?? `NT$${result.product.fiatPriceTwd}`}`);
    if (result.expireDate) {
        lines.push(`**繳費期限：** ${result.expireDate}`);
    }
    if (result.paymentUrl) {
        lines.push(`**繳費說明：** ${result.paymentUrl}`);
    }
    if (result.fulfillmentWarning) {
        lines.push(`\n${result.fulfillmentWarning}`);
    }
    lines.push('\n請在付款期限內完成付款，否則訂單將自動轉為逾期取消狀態。');
    return lines.join('\n');
}
export class FiatOrderService {
    productService;
    ecpayCvsPaymentService;
    fiatOrderRepository;
    log;
    constructor(productService, ecpayCvsPaymentService, fiatOrderRepository, logger) {
        this.productService = productService;
        this.ecpayCvsPaymentService = ecpayCvsPaymentService;
        this.fiatOrderRepository = fiatOrderRepository;
        this.log = logger ?? pino({ level: 'warn' });
    }
    /**
     * Creates a fiat-only order.
     * The tradeDesc parameter is an extension beyond spec R4.1.
     * When omitted, a default description is used.
     */
    async createFiatOnlyOrder(guildId, userId, productId, tradeDesc) {
        const product = await this.productService.findById(productId);
        if (!product || product.guildId !== guildId) {
            return err(DomainError.invalidInput('找不到該商品'));
        }
        if (!hasFiatPriceTwd(product)) {
            return err(DomainError.invalidInput('此商品尚未設定法幣價格'));
        }
        if (!isFiatOnly(product)) {
            return err(DomainError.invalidInput('此商品並非限定法幣支付商品'));
        }
        if (product.id === null) {
            return err(DomainError.unexpectedFailure('商品資料異常，缺少商品編號'));
        }
        const paymentResult = await this.ecpayCvsPaymentService.generateCvsPaymentCode(product.fiatPriceTwd, product.name, tradeDesc ?? `Discord 商品下單 user:${userId}`);
        if (paymentResult.isErr()) {
            return err(paymentResult.getError());
        }
        const paymentCode = paymentResult.getValue();
        try {
            const order = createPending(guildId, userId, product.id, product.name, product.rewardType, product.rewardAmount, product.autoCreateEscortOrder, product.escortOptionCode, paymentCode.orderNumber, paymentCode.paymentNo, product.fiatPriceTwd, paymentCode.expireAt);
            const savedOrder = await this.fiatOrderRepository.save(order);
            const fulfillmentSnapshot = {
                id: savedOrder.productId,
                guildId: savedOrder.guildId,
                name: savedOrder.productName,
                description: null,
                rewardType: savedOrder.fulfillmentRewardType,
                rewardAmount: savedOrder.fulfillmentRewardAmount,
                currencyPrice: null,
                fiatPriceTwd: savedOrder.amountTwd,
                autoCreateEscortOrder: savedOrder.fulfillmentAutoCreateEscortOrder,
                escortOptionCode: savedOrder.fulfillmentEscortOptionCode,
                createdAt: savedOrder.createdAt,
                updatedAt: savedOrder.updatedAt,
            };
            return ok({
                product: fulfillmentSnapshot,
                orderNumber: paymentCode.orderNumber,
                paymentNo: paymentCode.paymentNo,
                expireDate: paymentCode.expireDate,
                paymentUrl: paymentCode.paymentUrl,
                fulfillmentWarning: null,
            });
        }
        catch (e) {
            if (e instanceof Error && e.message) {
                return err(DomainError.invalidInput(e.message));
            }
            this.log.error({ guildId, userId, productId, orderNumber: paymentCode.orderNumber }, 'Failed to persist fiat order');
            return err(DomainError.persistenceFailure('建立法幣訂單失敗，請稍後再試'));
        }
    }
}
//# sourceMappingURL=fiat-order.service.js.map