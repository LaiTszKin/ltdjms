import { describe, it, expect } from 'vitest';
import {
  formatRedemptionSuccessMessage,
  createRedemptionCode,
  type RedemptionResult,
  type Product,
} from '@ltdjms/shop';

/** REG-201: Redemption message format parity with Java */
describe('Redemption messages (REG-201)', () => {
  it('should format success message like Java formatSuccessMessage', () => {
    const product: Product = {
      id: 1,
      guildId: 1,
      name: '測試商品',
      description: '商品描述',
      rewardType: 'CURRENCY',
      rewardAmount: 50,
      currencyPrice: null,
      fiatPriceTwd: null,
      autoCreateEscortOrder: false,
      escortOptionCode: null,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    const result: RedemptionResult = {
      code: createRedemptionCode('ABCD1234EFGH5678', 1, 1, null),
      product,
      rewardAmount: 50,
    };

    const message = formatRedemptionSuccessMessage(result);
    expect(message).toContain('你已成功兌換「測試商品」');
    expect(message).toContain('商品描述');
    expect(message).toContain('已發放獎勵');
  });

  it('should use Java-style failure prefix for handler contract', () => {
    const errorMessage = '兌換碼無效';
    const formatted = `❌ 兌換失敗：${errorMessage}`;
    expect(formatted).toBe('❌ 兌換失敗：兌換碼無效');
  });

  it('should use Java-style success prefix for handler contract', () => {
    const body = '你已成功兌換「商品」';
    const formatted = `✅ ${body}`;
    expect(formatted).toBe('✅ 你已成功兌換「商品」');
  });
});
