import { Result, ok, err, DomainError } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import { type FiatOrder, createPending } from '../domain/fiat-order.js';
import { EcpayCvsPaymentService, type CvsPaymentCode } from './ecpay-cvs-payment.service.js';
import {
  type Product,
  type RewardType,
  hasFiatPriceTwd,
  isFiatOnly,
  formatFiatPriceTwd,
} from '../domain/product-types.js';
import pino from 'pino';

export interface FiatOrderResult {
  product: Product;
  orderNumber: string;
  paymentNo: string;
  expireDate: string | null;
  paymentUrl: string | null;
  /**
   * Reserved for future use: when fulfillment encounters non-blocking warnings
   * (e.g., reward grant failed but order was already marked paid), this field
   * will carry the warning message. Currently always null.
   */
  fulfillmentWarning: string | null;
}

export function formatFiatOrderDMMessage(result: FiatOrderResult): string {
  const lines: string[] = [];
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
  private readonly log: pino.Logger;

  constructor(
    private readonly productService: { findById(productId: number): Promise<Product | null> },
    private readonly ecpayCvsPaymentService: EcpayCvsPaymentService,
    private readonly fiatOrderRepository: FiatOrderRepository,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  /**
   * Creates a fiat-only order.
   * The tradeDesc parameter is an extension beyond spec R4.1; when omitted, a default
   * description ("Discord 商品下單 user:{userId}") is sent to ECPay as the TradeDesc.
   * @param guildId - The guild ID
   * @param userId - The buyer user ID
   * @param productId - The product ID
   * @param tradeDesc - Optional custom ECPay TradeDesc; defaults to a descriptive string
   */
  async createFiatOnlyOrder(
    guildId: number,
    userId: number,
    productId: number,
    tradeDesc?: string,
  ): Promise<Result<FiatOrderResult, DomainError>> {
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

    const paymentResult = await this.ecpayCvsPaymentService.generateCvsPaymentCode(
      product.fiatPriceTwd!,
      product.name,
      tradeDesc ?? `Discord 商品下單 user:${userId}`,
    );
    if (paymentResult.isErr()) {
      return err(paymentResult.getError());
    }

    const paymentCode = paymentResult.getValue();
    try {
      const order = createPending(
        guildId,
        userId,
        product.id,
        product.name,
        product.rewardType as RewardType | null,
        product.rewardAmount,
        product.autoCreateEscortOrder,
        product.escortOptionCode,
        paymentCode.orderNumber,
        paymentCode.paymentNo,
        product.fiatPriceTwd!,
        paymentCode.expireAt,
      );
      const savedOrder = await this.fiatOrderRepository.save(order);
      const fulfillmentSnapshot: Product = {
        id: savedOrder.productId,
        guildId: savedOrder.guildId,
        name: savedOrder.productName,
        description: null,
        rewardType: savedOrder.fulfillmentRewardType as any,
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
    } catch (e: any) {
      this.log.error(
        { guildId, userId, productId, orderNumber: paymentCode.orderNumber, error: e },
        'Failed to persist fiat order',
      );
      if (e instanceof Error && e.message) {
        return err(DomainError.persistenceFailure(`建立法幣訂單失敗：${e.message}`));
      }
      return err(DomainError.persistenceFailure('建立法幣訂單失敗，請稍後再試'));
    }
  }
}
