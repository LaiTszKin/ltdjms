import crypto from 'node:crypto';
import { CODE_LENGTH, CODE_CHARACTERS } from '../domain/redemption-code.js';

export class RedemptionCodeGenerator {
  generate(): string {
    const chars: string[] = [];
    for (let i = 0; i < CODE_LENGTH; i++) {
      const index = crypto.randomInt(CODE_CHARACTERS.length);
      chars.push(CODE_CHARACTERS.charAt(index));
    }
    return chars.join('');
  }

  static isValidFormat(code: string): boolean {
    if (!code || code.length !== CODE_LENGTH) return false;
    const upper = code.toUpperCase();
    for (const ch of upper) {
      if (CODE_CHARACTERS.indexOf(ch) === -1) return false;
    }
    return true;
  }
}
