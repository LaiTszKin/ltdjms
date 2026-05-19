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
  private readonly alphanumeric: string;

  constructor(clock?: () => number) {
    this.clock = clock ?? (() => Date.now());
    this.alphanumeric = EscortDispatchOrderNumberGenerator.ALPHANUMERIC;
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
    const len = this.alphanumeric.length;
    for (let i = 0; i < EscortDispatchOrderNumberGenerator.SUFFIX_LENGTH; i++) {
      const idx = randomInt(0, len);
      chars.push(this.alphanumeric[idx]);
    }
    return chars.join('');
  }
}
