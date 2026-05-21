import { Result, ok, err, DomainError } from '@ltdjms/shared';
import {
  type Product,
  type RewardType,
  hasCurrencyPrice,
  hasReward,
  formatReward,
} from '../domain/product-types.js';
import pino from 'pino';

export interface PurchaseResult {
  product: Product;
  previousBalance: number;
  newBalance: number;
  price: number;
  rewardMessage: string;
}

export function formatPurchaseSuccessMessage(result: PurchaseResult): string {
  const lines: string[] = [];
  lines.push('✅ 購買成功！\n');
  lines.push(`**商品：** ${result.product.name}`);
  lines.push(`**價格：** ${result.price.toLocaleString()} 貨幣`);
  lines.push(`**購買前餘額：** ${result.previousBalance.toLocaleString()} 貨幣`);
  lines.push(`**購買後餘額：** ${result.newBalance.toLocaleString()} 貨幣`);
  if (result.rewardMessage) {
    lines.push(result.rewardMessage);
  }
  return lines.join('\n');
}

export class CurrencyPurchaseService {
  private readonly log: pino.Logger;

  constructor(
    private readonly productService: { findById(productId: number): Promise<Product | null> },
    private readonly balanceService: {
      tryGetBalance(
        guildId: number,
        userId: string,
      ): Promise<Result<{ balance: number }, DomainError>>;
    },
    private readonly balanceAdjustmentService: {
      tryAdjustBalance(
        guildId: number,
        userId: string,
        amount: number,
      ): Promise<Result<{ newBalance: number }, DomainError>>;
    },
    private readonly productRewardService: {
      grantReward(request: {
        guildId: number;
        userId: string;
        product: Product;
        amount: number;
        description: string;
      }): Promise<
        Result<
          { amount: number; currencyBalanceAfter: number | null },
          DomainError
        >
      >;
    },
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async purchaseProduct(
    guildId: number,
    userId: string,
    productId: number,
  ): Promise<Result<PurchaseResult, DomainError>> {
    const product = await this.productService.findById(productId);
    if (!product) {
      return err(DomainError.invalidInput('找不到該商品'));
    }
    if (product.guildId !== guildId) {
      return err(DomainError.invalidInput('找不到該商品'));
    }
    if (!hasCurrencyPrice(product)) {
      return err(DomainError.invalidInput('此商品不可用貨幣購買'));
    }

    const price = product.currencyPrice!;

    const balanceResult = await this.balanceService.tryGetBalance(guildId, userId);
    if (balanceResult.isErr()) {
      return err(balanceResult.getError());
    }

    const currentBalance = balanceResult.getValue().balance;
    if (currentBalance < price) {
      return err(
        DomainError.invalidInput(
          `餘額不足。需要: ${price.toLocaleString()} 貨幣，目前餘額: ${currentBalance.toLocaleString()} 貨幣`,
        ),
      );
    }

    const adjustResult = await this.balanceAdjustmentService.tryAdjustBalance(
      guildId,
      userId,
      -price,
    );
    if (adjustResult.isErr()) {
      this.log.error({ guildId, userId, productId }, 'Failed to deduct currency for purchase');
      return err(DomainError.persistenceFailure('扣除貨幣失敗'));
    }

    const purchaseBalance = adjustResult.getValue().newBalance;

    let finalBalance = purchaseBalance;
    let rewardMessage = '';

    if (hasReward(product)) {
      const rewardResult = await this.productRewardService.grantReward({
        guildId,
        userId,
        product,
        amount: product.rewardAmount!,
        description: `商品獎勵: ${product.name}`,
      });
      if (rewardResult.isErr()) {
        return await this.refundPurchaseAfterRewardFailure(
          guildId,
          userId,
          product,
          price,
          rewardResult.getError(),
          productId,
        );
      }

      const grantedReward = rewardResult.getValue();
      if (grantedReward.currencyBalanceAfter !== null) {
        finalBalance = grantedReward.currencyBalanceAfter;
      }
      rewardMessage = `\n\n獲得獎勵: ${formatReward(product)}`;
    }

    this.log.info({ guildId, userId, productId, price }, 'Product purchased');

    return ok({
      product,
      previousBalance: currentBalance,
      newBalance: finalBalance,
      price,
      rewardMessage,
    });
  }

  private async refundPurchaseAfterRewardFailure(
    guildId: number,
    userId: string,
    product: Product,
    price: number,
    rewardError: DomainError,
    productId: number,
  ): Promise<Result<PurchaseResult, DomainError>> {
    this.log.error(
      { guildId, userId, productId, reason: rewardError.message },
      'Failed to grant reward for purchased product',
    );

    const refundResult = await this.balanceAdjustmentService.tryAdjustBalance(
      guildId,
      userId,
      price,
    );
    if (refundResult.isErr()) {
      this.log.error(
        { guildId, userId, productId, reason: refundResult.getError().message },
        'Failed to refund purchase after reward failure',
      );
      return err(DomainError.persistenceFailure('商品獎勵發放失敗，且自動退款失敗'));
    }

    return err(DomainError.unexpectedFailure('商品獎勵發放失敗，已自動退款'));
  }
}
