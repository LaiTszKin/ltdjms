import { Result, ok, err, DomainError } from '@ltdjms/shared';
import type { RedemptionCodeRepository, CodeStats } from '../domain/redemption-code-repository.js';
import {
  type RedemptionCode,
  createRedemptionCode,
  withRedeemed,
  belongsToGuild,
  isInvalidated,
  isRedeemed,
  isExpired,
  getMaskedCode,
} from '../domain/redemption-code.js';
import {
  type Product,
  type RewardType,
  hasReward,
  formatReward,
} from '../domain/product-types.js';
import { RedemptionCodeGenerator } from './redemption-code-generator.js';
import pino from 'pino';

const MAX_BATCH_SIZE = 100;

export interface RedemptionResult {
  code: RedemptionCode;
  product: Product;
  rewardedAmount: number | null;
}

export function formatRedemptionSuccessMessage(result: RedemptionResult): string {
  const parts: string[] = [];
  parts.push(`你已成功兌換「${result.product.name}」`);
  if (result.product.description) {
    parts.push(result.product.description);
  }
  if (result.rewardedAmount !== null && hasReward(result.product)) {
    const formatted = formatReward(result.product);
    parts.push(`已發放獎勵：${formatted}`);
  }
  return parts.join('\n');
}

export interface CodePage {
  codes: RedemptionCode[];
  currentPage: number;
  totalPages: number;
  totalCount: number;
  pageSize: number;
}

export class RedemptionService {
  private readonly log: pino.Logger;

  constructor(
    private readonly codeRepository: RedemptionCodeRepository,
    private readonly productRepository: {
      findById(productId: number): Promise<Product | null>;
    },
    private readonly codeGenerator: RedemptionCodeGenerator,
    private readonly productRewardService: {
      grantReward(request: {
        guildId: number;
        userId: string;
        product: Product;
        amount: number;
        description: string;
      }): Promise<Result<{ amount: number }, DomainError>>;
    },
    private readonly transactionService: {
      recordTransaction(
        guildId: number,
        userId: string,
        product: Product,
        code: RedemptionCode,
      ): Promise<any>;
    },
    private readonly eventPublisher: { publish(event: any): void },
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async generateCodes(
    productId: number,
    count: number,
    expiresAt: Date | null,
    quantity = 1,
  ): Promise<Result<RedemptionCode[], DomainError>> {
    if (count <= 0) {
      return err(DomainError.invalidInput('生成數量必須大於 0'));
    }
    if (count > MAX_BATCH_SIZE) {
      return err(
        DomainError.invalidInput(`單次最多生成 ${MAX_BATCH_SIZE} 個兌換碼`),
      );
    }
    if (quantity <= 0) {
      return err(DomainError.invalidInput('兌換數量必須大於 0'));
    }
    if (quantity > 1000) {
      return err(DomainError.invalidInput('單個兌換碼最多可兌換 1000 個商品'));
    }
    if (expiresAt && expiresAt <= new Date()) {
      return err(DomainError.invalidInput('過期時間必須在未來'));
    }

    const product = await this.productRepository.findById(productId);
    if (!product) {
      return err(DomainError.invalidInput('找不到商品'));
    }

    const codes: RedemptionCode[] = [];
    for (let i = 0; i < count; i++) {
      const codeStr = await this.generateUniqueCode();
      codes.push(createRedemptionCode(codeStr, product.id!, product.guildId, expiresAt, quantity));
    }

    try {
      const savedCodes = await this.codeRepository.saveAll(codes);
      this.eventPublisher.publish({
        eventType: 'redemption_codes_generated',
        guildId: product.guildId,
        productId: product.id,
        count: savedCodes.length,
      });
      this.log.info(
        { count: savedCodes.length, productId },
        'Generated redemption codes',
      );
      return ok(savedCodes);
    } catch (e) {
      this.log.error({ productId, error: e }, 'Failed to generate codes');
      return err(DomainError.persistenceFailure('生成兌換碼失敗'));
    }
  }

  async redeemCode(
    codeStr: string,
    guildId: number,
    userId: string,
  ): Promise<Result<RedemptionResult, DomainError>> {
    if (!codeStr || codeStr.trim().length === 0) {
      return err(DomainError.invalidInput('兌換碼無效'));
    }

    codeStr = codeStr.trim().toUpperCase();
    const code = await this.codeRepository.findByCode(codeStr);
    if (!code) {
      this.log.debug({ codeStr }, 'Redemption code not found');
      return err(DomainError.invalidInput('兌換碼無效'));
    }

    if (!belongsToGuild(code, guildId)) {
      this.log.debug({ codeStr, guildId }, 'Redemption code does not belong to guild');
      return err(DomainError.invalidInput('兌換碼無效'));
    }
    if (isInvalidated(code)) {
      this.log.debug({ codeStr }, 'Redemption code has been invalidated');
      return err(DomainError.invalidInput('此兌換碼已失效'));
    }
    if (isRedeemed(code)) {
      this.log.debug({ codeStr }, 'Redemption code already redeemed');
      return err(DomainError.invalidInput('此兌換碼已被使用'));
    }
    if (isExpired(code)) {
      this.log.debug({ codeStr }, 'Redemption code has expired');
      return err(DomainError.invalidInput('此兌換碼已過期'));
    }
    if (code.productId === null) {
      this.log.error({ codeId: code.id }, 'Product ID is null for redemption code');
      return err(DomainError.invalidInput('此兌換碼已失效'));
    }

    const product = await this.productRepository.findById(code.productId);
    if (!product) {
      this.log.error({ productId: code.productId }, 'Product not found for redemption code');
      return err(DomainError.unexpectedFailure('商品資料異常'));
    }

    // Calculate total reward with overflow detection
    let totalRewardAmount: number | null = null;
    if (hasReward(product)) {
      const rewardCalc = this.calculateTotalRewardAmount(product, code);
      if (rewardCalc.isErr()) {
        return err(rewardCalc.getError());
      }
      totalRewardAmount = rewardCalc.getValue();
    }

    try {
      if (code.id === null) {
        this.log.error({ code: getMaskedCode(code) }, 'Redemption code ID is null during redeem');
        return err(DomainError.unexpectedFailure('兌換碼資料異常'));
      }

      const redeemedResult = withRedeemed(code, userId);
      if (redeemedResult.isErr()) {
        return err(redeemedResult.getError());
      }
      const redeemedCode = redeemedResult.getValue();
      const marked = await this.codeRepository.markAsRedeemedIfAvailable(
        redeemedCode.id!,
        userId,
        redeemedCode.redeemedAt!,
      );
      if (!marked) {
        this.log.warn(
          { code: getMaskedCode(code), userId },
          'Redemption code became unavailable during redeem attempt',
        );
        return err(DomainError.invalidInput('此兌換碼已被使用或不可用'));
      }

      let rewardedAmount: number | null = null;
      if (hasReward(product)) {
        const rewardResult = await this.productRewardService.grantReward({
          guildId,
          userId,
          product,
          amount: totalRewardAmount!,
          description: `兌換碼: ${getMaskedCode(code)} (${product.name}) x${code.quantity}`,
        });
        if (rewardResult.isErr()) {
          const rollbackError = await this.rollbackRedeemedCodeAfterRewardFailure(
            redeemedCode,
            userId,
            rewardResult.getError(),
            product.name,
          );
          return err(rollbackError);
        }
        rewardedAmount = rewardResult.getValue().amount;
      }

      const transaction = await this.transactionService.recordTransaction(
        guildId,
        userId,
        product,
        redeemedCode,
      );

      this.eventPublisher.publish({
        eventType: 'product_redemption_completed',
        guildId,
        userId,
        transaction,
        timestamp: new Date(),
      });

      this.log.info(
        { code: getMaskedCode(code), userId, productName: product.name },
        'Successfully redeemed code',
      );

      return ok({ code: redeemedCode, product, rewardedAmount });
    } catch (e) {
      this.log.error({ code: getMaskedCode(code), error: e }, 'Failed to redeem code');
      return err(DomainError.persistenceFailure('兌換失敗'));
    }
  }

  async findByCode(codeStr: string): Promise<RedemptionCode | null> {
    if (!codeStr || codeStr.trim().length === 0) return null;
    return this.codeRepository.findByCode(codeStr.trim().toUpperCase());
  }

  async getCodePage(productId: number, page: number, pageSize: number): Promise<CodePage> {
    if (page < 1) page = 1;
    if (pageSize < 1) pageSize = 10;

    const totalCount = await this.codeRepository.countByProductId(productId);
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    if (page > totalPages) page = totalPages;

    const offset = (page - 1) * pageSize;
    const codes = await this.codeRepository.findByProductId(productId, pageSize, offset);

    return { codes, currentPage: page, totalPages, totalCount, pageSize };
  }

  async getCodeStats(productId: number): Promise<CodeStats> {
    return this.codeRepository.getStatsByProductId(productId);
  }

  private async generateUniqueCode(): Promise<string> {
    const maxAttempts = 10;
    for (let i = 0; i < maxAttempts; i++) {
      const code = this.codeGenerator.generate();
      const existing = await this.codeRepository.findByCode(code);
      if (!existing) {
        return code;
      }
      this.log.debug({ code, attempt: i + 1 }, 'Duplicate code generated, retrying');
    }
    throw new Error(
      `Failed to generate unique code after ${maxAttempts} attempts`,
    );
  }

  private async rollbackRedeemedCodeAfterRewardFailure(
    redeemedCode: RedemptionCode,
    userId: string,
    rewardError: DomainError,
    productName: string,
  ): Promise<DomainError> {
    this.log.error(
      { code: getMaskedCode(redeemedCode), productName, reason: rewardError.message },
      'Failed to grant reward for redeemed code',
    );

    const reverted = await this.codeRepository.clearRedeemedIfMatches(
      redeemedCode.id!,
      userId,
      redeemedCode.redeemedAt!,
    );
    if (reverted) {
      return DomainError.unexpectedFailure('商品獎勵發放失敗，兌換已取消');
    }
    return DomainError.persistenceFailure('商品獎勵發放失敗，且兌換碼回復失敗');
  }

  private calculateTotalRewardAmount(
    product: Product,
    code: RedemptionCode,
  ): Result<number, DomainError> {
    const rewardAmount = product.rewardAmount!;
    const quantity = code.quantity;
    if (rewardAmount > Number.MAX_SAFE_INTEGER / quantity) {
      return err(DomainError.invalidInput('商品獎勵計算超出範圍'));
    }
    const total = rewardAmount * quantity;
    if (total <= 0) {
      return err(DomainError.invalidInput('商品獎勵金額無效'));
    }
    return ok(total);
  }
}
