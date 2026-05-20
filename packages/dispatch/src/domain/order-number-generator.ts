import { randomInt } from 'node:crypto';

/**
 * 護航派單訂單編號產生器。
 * 格式：ESC-YYYYMMDD-XXXXXX（6 位英數字尾碼，排除 I、O、0、1 等混淆字元）
 */
export class EscortDispatchOrderNumberGenerator {
  private static readonly PREFIX = 'ESC';
  private static readonly SUFFIX_LENGTH = 6;
  private static readonly ALPHANUMERIC = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

  private readonly clock: () => number;

  constructor(clock?: () => number) {
    this.clock = clock ?? (() => Date.now());
  }

  /** 產生一組訂單編號。 */
  generate(): string {
    const now = new Date(this.clock());
    const datePart = [
      now.getUTCFullYear().toString(),
      String(now.getUTCMonth() + 1).padStart(2, '0'),
      String(now.getUTCDate()).padStart(2, '0'),
    ].join('');

    return `${EscortDispatchOrderNumberGenerator.PREFIX}-${datePart}-${this.randomSuffix()}`;
  }

  private randomSuffix(): string {
    const chars: string[] = [];
    const len = EscortDispatchOrderNumberGenerator.ALPHANUMERIC.length;
    for (let i = 0; i < EscortDispatchOrderNumberGenerator.SUFFIX_LENGTH; i++) {
      // randomInt(min, max): max is exclusive, so range is [0, len)
      const idx = randomInt(0, len);
      chars.push(EscortDispatchOrderNumberGenerator.ALPHANUMERIC[idx]);
    }
    return chars.join('');
  }
}

/**
 * 嘗試產生一個在資料庫中不重複的訂單編號。
 * @param generator 訂單編號產生器
 * @param existsFn 檢查編號是否已存在的回呼函數
 * @param maxRetries 最大重試次數（預設 20 次）
 * @returns 唯一的訂單編號
 * @throws 若超過最大重試次數仍無法產生唯一編號
 */
export async function generateUniqueOrderNumber(
  generator: EscortDispatchOrderNumberGenerator,
  existsFn: (orderNumber: string) => Promise<boolean>,
  maxRetries = 20,
): Promise<string> {
  for (let i = 0; i < maxRetries; i++) {
    const candidate = generator.generate();
    const exists = await existsFn(candidate);
    if (!exists) {
      return candidate;
    }
  }
  throw new Error('Unable to generate unique order number after retries');
}
