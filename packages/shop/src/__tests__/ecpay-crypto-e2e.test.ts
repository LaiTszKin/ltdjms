import { describe, it, expect } from 'vitest';
import { encryptAES, decryptAES } from '../crypto/ecpay-aes.js';
import { buildCheckMacValue } from '../crypto/ecpay-checkmac.js';
import { javaUrlEncode, javaUrlDecode } from '../crypto/url-encoder.js';

const itE2E = process.env.RUN_ECPAY_E2E === 'true' ? it : it.skip;

// ECPay E2E test credentials (stage environment)
const HASH_KEY = 'ejCk326UnaZWKisg';
const HASH_IV = 'q9jcZX8Ib9LM8wYk';

describe('ECPay Crypto E2E', () => {
  // =======================================================================
  // AES-CBC Encrypt/Decrypt Round-trip
  // =======================================================================

  itE2E('should roundtrip a simple JSON payload', () => {
    const plain = '{"MerchantID":"2000132","ChoosePayment":"CVS"}';
    const encrypted = encryptAES(plain, HASH_KEY, HASH_IV);
    expect(encrypted).toBeTruthy();
    expect(typeof encrypted).toBe('string');

    const decrypted = decryptAES(encrypted, HASH_KEY, HASH_IV);
    expect(decrypted).toBe(plain);
  });

  itE2E('should roundtrip a full CVS payment request payload', () => {
    const payload = JSON.stringify({
      MerchantID: '2000132',
      MerchantTradeNo: 'FD250522120000000001',
      MerchantTradeDate: '2026/05/22 12:00:00',
      TotalAmount: 500,
      TradeDesc: 'E2E Test Order',
      ItemName: 'E2E Test Product',
      ReturnURL: 'https://example.com/ecpay/callback',
      ChoosePayment: 'CVS',
    });

    const encrypted = encryptAES(payload, HASH_KEY, HASH_IV);
    const decrypted = decryptAES(encrypted, HASH_KEY, HASH_IV);
    expect(decrypted).toBe(payload);
  });

  itE2E('should roundtrip a callback data payload', () => {
    const payload = JSON.stringify({
      MerchantTradeNo: 'E2E-CB-001',
      TradeStatus: '1',
      SimulatePaid: '1',
      RtnCode: '1',
      RtnMsg: 'Succeeded',
      TradeAmt: 1000,
      MerchantID: '2000132',
      PaymentType: 'CVS',
    });

    const encrypted = encryptAES(payload, HASH_KEY, HASH_IV);
    const decrypted = decryptAES(encrypted, HASH_KEY, HASH_IV);
    expect(decrypted).toBe(payload);

    // Verify all fields survive the roundtrip
    const parsed = JSON.parse(decrypted);
    expect(parsed.MerchantTradeNo).toBe('E2E-CB-001');
    expect(parsed.TradeStatus).toBe('1');
    expect(parsed.SimulatePaid).toBe('1');
    expect(parsed.TradeAmt).toBe(1000);
    expect(parsed.MerchantID).toBe('2000132');
  });

  itE2E('should handle special characters through roundtrip', () => {
    const payload = JSON.stringify({
      ItemName: '測試商品 A&B (special) ~重要!',
      TradeDesc: 'Discord 商品下單 *優惠*',
    });

    const encrypted = encryptAES(payload, HASH_KEY, HASH_IV);
    const decrypted = decryptAES(encrypted, HASH_KEY, HASH_IV);
    expect(decrypted).toBe(payload);
  });

  itE2E('should handle URL values with query parameters', () => {
    const payload = JSON.stringify({
      ReturnURL: 'https://example.com/ecpay/callback?order=123&status=ok&sig=abc',
      ClientBackURL: 'https://shop.example.com/return?foo=bar&baz=qux',
    });

    const encrypted = encryptAES(payload, HASH_KEY, HASH_IV);
    const decrypted = decryptAES(encrypted, HASH_KEY, HASH_IV);
    expect(decrypted).toBe(payload);
  });

  itE2E('should produce deterministic ciphertext for identical inputs', () => {
    const payload = '{"MerchantID":"2000132","TotalAmount":500}';
    const cipher1 = encryptAES(payload, HASH_KEY, HASH_IV);
    const cipher2 = encryptAES(payload, HASH_KEY, HASH_IV);
    expect(cipher1).toBe(cipher2);
  });

  itE2E('should produce base64 ciphertext block-aligned to 16 bytes', () => {
    const payload = '{"MerchantID":"2000132","TotalAmount":500}';
    const cipher = encryptAES(payload, HASH_KEY, HASH_IV);
    const decoded = Buffer.from(cipher, 'base64');
    expect(decoded.length % 16).toBe(0);
  });

  // =======================================================================
  // CheckMacValue with E2E credentials
  // =======================================================================

  itE2E('should produce valid CheckMacValue with E2E credentials', () => {
    const params: Record<string, string> = {
      MerchantID: '2000132',
      MerchantTradeNo: 'E2E-TEST-001',
      TimeStamp: Math.floor(Date.now() / 1000).toString(),
    };

    const result = buildCheckMacValue(params, HASH_KEY, HASH_IV);
    expect(result).toMatch(/^[A-F0-9]{64}$/);
  });

  itE2E('should produce deterministic CheckMacValue', () => {
    const params: Record<string, string> = {
      MerchantID: '2000132',
      MerchantTradeNo: 'E2E-TEST-001',
      TimeStamp: '1000000000',
    };

    const result1 = buildCheckMacValue(params, HASH_KEY, HASH_IV);
    const result2 = buildCheckMacValue(params, HASH_KEY, HASH_IV);
    expect(result1).toBe(result2);
  });

  itE2E('should compute CheckMacValue from callback outer params', () => {
    const fakeEncryptedData = 'abc123def456==';
    const params: Record<string, string> = {
      Data: fakeEncryptedData,
      MerchantID: '2000132',
    };

    const result = buildCheckMacValue(params, HASH_KEY, HASH_IV);
    expect(result).toMatch(/^[A-F0-9]{64}$/);
  });

  // =======================================================================
  // Java URL Encoding Compatibility
  // =======================================================================

  itE2E('should encode space as + (Java URLEncoder behavior)', () => {
    expect(javaUrlEncode('test value')).toBe('test+value');
  });

  itE2E('should encode parentheses as %28 %29', () => {
    expect(javaUrlEncode('test (1)')).toBe('test+%281%29');
  });

  itE2E('should NOT encode asterisk (Java URLEncoder behavior)', () => {
    expect(javaUrlEncode('test *A*')).toBe('test+*A*');
  });

  itE2E('should roundtrip javaUrlEncode/javaUrlDecode', () => {
    const original = '{"key":"test value with *special* chars (1) ~tilde!"}';
    const encoded = javaUrlEncode(original);
    const decoded = javaUrlDecode(encoded);
    expect(decoded).toBe(original);
  });

  itE2E('should handle URL-encoded strings used in ECPay callback Data field', () => {
    // Simulate a callback data JSON before AES encryption
    const callbackJson = JSON.stringify({
      MerchantTradeNo: 'E2E-CB-TEST',
      TradeStatus: '1',
      SimulatePaid: '1',
      RtnCode: '1',
      MerchantID: '2000132',
    });

    const encrypted = encryptAES(callbackJson, HASH_KEY, HASH_IV);
    const decrypted = decryptAES(encrypted, HASH_KEY, HASH_IV);

    expect(decrypted).toBe(callbackJson);
    const parsed = JSON.parse(decrypted);
    expect(parsed.MerchantTradeNo).toBe('E2E-CB-TEST');
    expect(parsed.TradeStatus).toBe('1');
  });
});
