/**
 * P0-1: ECPay Crypto Golden-Data Cross-Validation Tests
 *
 * These tests verify the TypeScript ECPay crypto implementation against
 * deterministic golden-data fixtures. They serve as the TypeScript-side
 * anchor for cross-language validation:
 *
 * 1. AES-CBC encryption is deterministic given identical key/iv/plaintext.
 * 2. Step-by-step CheckMacValue reconstruction matches buildCheckMacValue().
 * 3. Full roundtrip preserves data integrity including special characters.
 *
 * When Java golden data (known ciphertext/CheckMacValue pairs) becomes
 * available, add the expected values as inline constants in this file
 * to enable byte-exact cross-language verification.
 */
import { describe, it, expect } from 'vitest';
import crypto from 'node:crypto';
import { encryptAES, decryptAES } from '../crypto/ecpay-aes.js';
import { buildCheckMacValue } from '../crypto/ecpay-checkmac.js';
import { javaUrlEncode } from '../crypto/url-encoder.js';

// ---------------------------------------------------------------------------
// Shared fixture credentials (standard ECPay test merchant)
// ---------------------------------------------------------------------------
const hashKey = 'pwFHCqoQZGmho4w6';
const hashIv = 'EkRm7iFT261dpevs';

// ---------------------------------------------------------------------------
// Fixture 1: Basic CVS payment (all required ECPay fields)
// ---------------------------------------------------------------------------
const fixturePlainText =
  '{"MerchantID":"3002607","MerchantTradeNo":"LTDJMS20260521000001",' +
  '"MerchantTradeDate":"2026/05/21 14:00:00","PaymentType":"aio",' +
  '"TotalAmount":100,"TradeDesc":"Test Order","ItemName":"Test Item",' +
  '"ReturnURL":"https://example.com/callback","ChoosePayment":"CVS"}';

const fixtureCheckMacParams: Record<string, string> = {
  MerchantID: '3002607',
  MerchantTradeNo: 'LTDJMS20260521000001',
  MerchantTradeDate: '2026/05/21 14:00:00',
  PaymentType: 'aio',
  TotalAmount: '100',
  TradeDesc: 'Test Order',
  ItemName: 'Test Item',
  ReturnURL: 'https://example.com/callback',
  ChoosePayment: 'CVS',
};

// ---------------------------------------------------------------------------
// Helpers shared between the implementation-under-test and the step-by-step
// reference reconstruction (kept in sync with ecpay-checkmac.ts).
// ---------------------------------------------------------------------------
const ecpaySubstitutions: [RegExp, string][] = [
  [/%2d/g, '-'],
  [/%5f/g, '_'],
  [/%2e/g, '.'],
  [/%21/g, '!'],
  [/%2a/g, '*'],
  [/%28/g, '('],
  [/%29/g, ')'],
  [/%20/g, '+'],
  [/%7e/g, '~'],
];

/** Rebuild the CheckMacValue step by step for cross-validation. */
function referenceCheckMacValue(
  params: Record<string, string>,
  key: string,
  iv: string,
): string {
  // Step 1-2: Sort params alphabetically, build check string
  const sorted = Object.entries(params)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .sort(([a], [b]) => a.localeCompare(b));

  let rawStr = `HashKey=${key}`;
  for (const [k, v] of sorted) {
    rawStr += `&${k}=${v}`;
  }
  rawStr += `&HashIV=${iv}`;

  // Step 3: Java URL encode and lowercase
  let encoded = javaUrlEncode(rawStr).toLowerCase();

  // Step 4: ECPay-specific URL encoding substitutions
  for (const [pattern, replacement] of ecpaySubstitutions) {
    encoded = encoded.replace(pattern, replacement);
  }

  // Step 5: SHA-256 hash and uppercase hex
  return crypto.createHash('sha256').update(encoded).digest('hex').toUpperCase();
}

// ===========================================================================
// AES-CBC Golden-Data Cross-Validation
// ===========================================================================
describe('P0-1: AES-CBC golden-data cross-validation', () => {
  // -----------------------------------------------------------------------
  // Deterministic output
  // -----------------------------------------------------------------------
  describe('deterministic encryption', () => {
    it('should produce identical ciphertext for identical inputs', () => {
      const cipher1 = encryptAES(fixturePlainText, hashKey, hashIv);
      const cipher2 = encryptAES(fixturePlainText, hashKey, hashIv);
      expect(cipher1).toBe(cipher2);
    });

    it('should produce valid base64 output matching expected pattern', () => {
      const cipher = encryptAES(fixturePlainText, hashKey, hashIv);
      expect(() => Buffer.from(cipher, 'base64')).not.toThrow();
      expect(cipher).toMatch(/^[A-Za-z0-9+/]+=*$/);
    });

    it('should have ciphertext block-aligned to 16 bytes (AES-CBC)', () => {
      const cipher = encryptAES(fixturePlainText, hashKey, hashIv);
      const decoded = Buffer.from(cipher, 'base64');
      expect(decoded.length % 16).toBe(0);
    });

    it('should produce different ciphertext when plaintext differs', () => {
      const cipher1 = encryptAES('{"a":"1"}', hashKey, hashIv);
      const cipher2 = encryptAES('{"a":"2"}', hashKey, hashIv);
      expect(cipher1).not.toBe(cipher2);
    });

    it('should produce different ciphertext when IV differs', () => {
      const cipher1 = encryptAES(fixturePlainText, hashKey, hashIv);
      const cipher2 = encryptAES(fixturePlainText, hashKey, 'DifferentIv1234567');
      expect(cipher1).not.toBe(cipher2);
    });

    it('should produce different ciphertext when key differs', () => {
      const cipher1 = encryptAES(fixturePlainText, hashKey, hashIv);
      const cipher2 = encryptAES(fixturePlainText, 'DifferentKey1234567', hashIv);
      expect(cipher1).not.toBe(cipher2);
    });
  });

  // -----------------------------------------------------------------------
  // Deterministic decryption
  // -----------------------------------------------------------------------
  describe('deterministic decryption', () => {
    it('should decrypt ciphertext to original fixture plaintext', () => {
      const cipher = encryptAES(fixturePlainText, hashKey, hashIv);
      const decrypted = decryptAES(cipher, hashKey, hashIv);
      expect(decrypted).toBe(fixturePlainText);
    });

    it('should produce identical decrypted output for same ciphertext', () => {
      const cipher = encryptAES(fixturePlainText, hashKey, hashIv);
      const dec1 = decryptAES(cipher, hashKey, hashIv);
      const dec2 = decryptAES(cipher, hashKey, hashIv);
      expect(dec1).toBe(dec2);
    });
  });
});

// ===========================================================================
// CheckMacValue Golden-Data Cross-Validation
// ===========================================================================
describe('P0-1: CheckMacValue golden-data cross-validation', () => {
  // -----------------------------------------------------------------------
  // Step-by-step algorithm verification against reference implementation
  // -----------------------------------------------------------------------
  describe('step-by-step algorithm verification', () => {
    it('should sort params alphabetically (ASCII order)', () => {
      const params: Record<string, string> = {
        BParam: '2',
        AParam: '1',
        CParam: '3',
      };
      const sorted = Object.entries(params)
        .filter(([, v]) => v !== '' && v !== null && v !== undefined)
        .sort(([a], [b]) => a.localeCompare(b));
      expect(sorted.map(([k]) => k)).toEqual(['AParam', 'BParam', 'CParam']);
    });

    it('should match reference step-by-step computation for fixture params', () => {
      const result = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      const expected = referenceCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      expect(result).toBe(expected);
    });

    it('should match reference for minimal params', () => {
      const params: Record<string, string> = {
        MerchantID: '3002607',
        MerchantTradeNo: 'TEST001',
        TimeStamp: '1000000',
      };
      const result = buildCheckMacValue(params, hashKey, hashIv);
      const expected = referenceCheckMacValue(params, hashKey, hashIv);
      expect(result).toBe(expected);
    });

    it('should match reference for params with special characters', () => {
      const params: Record<string, string> = {
        ItemName: 'test!product* (special) ~with_dashes',
        MerchantID: '3002607',
      };
      const result = buildCheckMacValue(params, hashKey, hashIv);
      const expected = referenceCheckMacValue(params, hashKey, hashIv);
      expect(result).toBe(expected);
    });

    it('should match reference for params containing Chinese characters', () => {
      const params: Record<string, string> = {
        MerchantID: '3002607',
        ItemName: '測試商品 A&B',
        TradeDesc: 'Discord 商品下單',
      };
      const result = buildCheckMacValue(params, hashKey, hashIv);
      const expected = referenceCheckMacValue(params, hashKey, hashIv);
      expect(result).toBe(expected);
    });

    it('should match reference for params with URL values', () => {
      const params: Record<string, string> = {
        MerchantID: '3002607',
        ReturnURL: 'https://example.com/callback?order=123&status=ok',
        ClientBackURL: 'https://shop.example.com/return?foo=bar&baz=qux',
      };
      const result = buildCheckMacValue(params, hashKey, hashIv);
      const expected = referenceCheckMacValue(params, hashKey, hashIv);
      expect(result).toBe(expected);
    });
  });

  // -----------------------------------------------------------------------
  // ECPay specification compliance
  // -----------------------------------------------------------------------
  describe('ECPay spec compliance', () => {
    it('should exclude empty and null params', () => {
      const paramsWithExtra: Record<string, string> = {
        ...fixtureCheckMacParams,
        EmptyField: '',
        UndefinedField: undefined as unknown as string,
      };
      const resultWith = buildCheckMacValue(paramsWithExtra, hashKey, hashIv);
      const resultBase = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      expect(resultWith).toBe(resultBase);
    });

    it('should produce 64-char uppercase hex SHA-256', () => {
      const result = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      expect(result).toMatch(/^[A-F0-9]{64}$/);
    });

    it('should apply ECPay URL encoding substitutions', () => {
      // The substitutions revert: %2d→-, %5f→_, %2e→., %21→!, %2a→*, %28→(, %29→), %20→+, %7e→~
      const params: Record<string, string> = {
        MerchantID: '3002607',
        ItemName: 'test-id_with.dots!star* (parens) ~tilde',
      };
      const result = buildCheckMacValue(params, hashKey, hashIv);
      expect(result).toMatch(/^[A-F0-9]{64}$/);
    });
  });

  // -----------------------------------------------------------------------
  // Determinism
  // -----------------------------------------------------------------------
  describe('determinism', () => {
    it('should produce identical hash for identical inputs', () => {
      const result1 = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      const result2 = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      expect(result1).toBe(result2);
    });

    it('should produce different hash when a parameter value changes', () => {
      const result1 = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      const modified = { ...fixtureCheckMacParams, TotalAmount: '200' };
      const result2 = buildCheckMacValue(modified, hashKey, hashIv);
      expect(result1).not.toBe(result2);
    });

    it('should produce different hash when a parameter is added', () => {
      const result1 = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
      const extended = { ...fixtureCheckMacParams, ExtraParam: 'extra' };
      const result2 = buildCheckMacValue(extended, hashKey, hashIv);
      expect(result1).not.toBe(result2);
    });
  });
});

// ===========================================================================
// End-to-End Fixture Validation
// ===========================================================================
describe('P0-1: end-to-end payment fixture validation', () => {
  it('should complete full AES roundtrip with fixture payment data', () => {
    const encrypted = encryptAES(fixturePlainText, hashKey, hashIv);
    expect(encrypted).toBeTruthy();
    expect(typeof encrypted).toBe('string');

    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(fixturePlainText);
  });

  it('should produce valid CheckMacValue for fixture payment params', () => {
    const checkMacValue = buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv);
    expect(checkMacValue).toMatch(/^[A-F0-9]{64}$/);
  });
});

// ===========================================================================
// Data Integrity Cross-Check
// ===========================================================================
describe('P0-1: data integrity cross-check', () => {
  it('should preserve all JSON fields through AES roundtrip', () => {
    const encrypted = encryptAES(fixturePlainText, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    const parsed = JSON.parse(decrypted);
    expect(parsed.MerchantID).toBe('3002607');
    expect(parsed.MerchantTradeNo).toBe('LTDJMS20260521000001');
    expect(parsed.TotalAmount).toBe(100);
    expect(parsed.ChoosePayment).toBe('CVS');
    expect(parsed.ReturnURL).toBe('https://example.com/callback');
  });

  it('should preserve special characters through AES roundtrip', () => {
    const specialText =
      '{"url":"https://example.com?a=1&b=2","desc":"商品 (測試) ~重要!","price":"100*"}';
    const encrypted = encryptAES(specialText, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(specialText);
  });

  it('should preserve Chinese characters through AES roundtrip', () => {
    const chineseText =
      '{"TradeDesc":"Discord 商品下單","ItemName":"測試商品 A&B"}';
    const encrypted = encryptAES(chineseText, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(chineseText);
  });

  it('should handle URL values with query params through AES roundtrip', () => {
    const urlText =
      '{"ReturnURL":"https://example.com/ecpay/callback?trade_no=LTDJMS001&status=1"}';
    const encrypted = encryptAES(urlText, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(urlText);
  });

  it('should produce deterministic AES output for identical fixture inputs', () => {
    // Run multiple times to confirm no hidden state or RNG affects output
    const results = Array.from({ length: 10 }, () =>
      encryptAES(fixturePlainText, hashKey, hashIv),
    );
    for (let i = 1; i < results.length; i++) {
      expect(results[i]).toBe(results[0]);
    }
  });

  it('should produce deterministic CheckMacValue for identical fixture inputs', () => {
    const results = Array.from({ length: 10 }, () =>
      buildCheckMacValue(fixtureCheckMacParams, hashKey, hashIv),
    );
    for (let i = 1; i < results.length; i++) {
      expect(results[i]).toBe(results[0]);
    }
  });
});
