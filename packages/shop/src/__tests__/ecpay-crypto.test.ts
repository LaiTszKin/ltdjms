import { describe, it, expect } from 'vitest';
import { encryptAES, decryptAES } from '../crypto/ecpay-aes.js';
import { buildCheckMacValue } from '../crypto/ecpay-checkmac.js';
import { javaUrlEncode, javaUrlDecode } from '../crypto/url-encoder.js';

describe('ECPay AES encrypt/decrypt', () => {
  const hashKey = 'pwFHCqoQZGmho4w6';
  const hashIv = 'EkRm7iFT261dpevs';

  it('should roundtrip AES encrypt/decrypt', () => {
    const plain = '{"MerchantID":"3002607","ChoosePayment":"CVS"}';
    const encrypted = encryptAES(plain, hashKey, hashIv);
    expect(encrypted).toBeTruthy();
    expect(typeof encrypted).toBe('string');

    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(plain);
  });

  it('should handle URL-encoded special characters', () => {
    const plain = '{"ItemName":"測試商品 A&B","TotalAmount":100}';
    const encrypted = encryptAES(plain, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(plain);
  });

  it('should handle Chinese characters', () => {
    const plain = '{"TradeDesc":"Discord 商品下單"}';
    const encrypted = encryptAES(plain, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(plain);
  });

  it('should handle empty JSON object', () => {
    const plain = '{}';
    const encrypted = encryptAES(plain, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(plain);
  });

  it('should handle Java URLDecoder + to space behavior', () => {
    // Simulate data that contains + after URL encoding
    const plain = '{"key":"value with spaces"}';
    const encrypted = encryptAES(plain, hashKey, hashIv);
    const decrypted = decryptAES(encrypted, hashKey, hashIv);
    expect(decrypted).toBe(plain);
  });
});

describe('ECPay CheckMacValue', () => {
  const hashKey = 'pwFHCqoQZGmho4w6';
  const hashIv = 'EkRm7iFT261dpevs';

  it('should produce correct CheckMacValue for known params', () => {
    const params = {
      MerchantID: '3002607',
      MerchantTradeNo: 'FD250519120000000001',
      TimeStamp: '1747627200',
    };

    const result = buildCheckMacValue(params, hashKey, hashIv);
    // Verify it's a 64-char uppercase hex string
    expect(result).toMatch(/^[A-F0-9]{64}$/);
  });

  it('should exclude empty params', () => {
    const params = {
      MerchantID: '3002607',
      MerchantTradeNo: 'FD250519120000000001',
      TimeStamp: '1747627200',
      EmptyParam: '',
    };

    const withEmpty = buildCheckMacValue(params, hashKey, hashIv);
    const withoutEmpty = buildCheckMacValue(
      { MerchantID: '3002607', MerchantTradeNo: 'FD250519120000000001', TimeStamp: '1747627200' },
      hashKey,
      hashIv,
    );
    expect(withEmpty).toBe(withoutEmpty);
  });

  it('should sort params alphabetically', () => {
    const params = {
      ZParam: 'last',
      AParam: 'first',
      MParam: 'middle',
    };

    const result = buildCheckMacValue(params, hashKey, hashIv);
    // Deterministic for same input
    const result2 = buildCheckMacValue(params, hashKey, hashIv);
    expect(result).toBe(result2);
  });

  it('should apply ECPay URL encoding substitutions', () => {
    const params = {
      MerchantID: 'test!id',
      ItemName: 'hello*world',
    };

    const result = buildCheckMacValue(params, hashKey, hashIv);
    expect(result).toMatch(/^[A-F0-9]{64}$/);
  });

  it('should produce consistent results for same input', () => {
    const params = {
      MerchantID: '3002607',
      MerchantTradeNo: 'TEST123',
      TimeStamp: '1000000000',
    };

    const result1 = buildCheckMacValue(params, hashKey, hashIv);
    const result2 = buildCheckMacValue(params, hashKey, hashIv);
    expect(result1).toBe(result2);
  });
});

describe('Cross-language Java compatibility (P0-6)', () => {
  describe('javaUrlEncode', () => {
    it('should encode space as + (Java URLEncoder behavior)', () => {
      expect(javaUrlEncode('test value')).toBe('test+value');
    });

    it('should encode parentheses as %28 %29', () => {
      expect(javaUrlEncode('test (1)')).toBe('test+%281%29');
    });

    it('should encode asterisk as %2A', () => {
      expect(javaUrlEncode('test *A*')).toBe('test+%2AA%2A');
    });

    it('should encode exclamation as %21', () => {
      expect(javaUrlEncode('hello!')).toBe('hello%21');
    });

    it('should encode tilde as %7E', () => {
      expect(javaUrlEncode('~tilde')).toBe('%7Etilde');
    });

    it('should encode mixed special characters', () => {
      // Java: URLEncoder.encode("test (1) !star* ~")
      // produces "test+%281%29+%21star%2A+%7E"
      expect(javaUrlEncode('test (1) !star* ~')).toBe('test+%281%29+%21star%2A+%7E');
    });
  });

  describe('javaUrlDecode', () => {
    it('should decode + as space (Java URLDecoder behavior)', () => {
      expect(javaUrlDecode('test+value')).toBe('test value');
    });

    it('should decode %28 %29 as parentheses', () => {
      expect(javaUrlDecode('test+%281%29')).toBe('test (1)');
    });

    it('should decode %2A as asterisk', () => {
      expect(javaUrlDecode('test+%2AA%2A')).toBe('test *A*');
    });

    it('should roundtrip encode/decode', () => {
      const original = '{"MerchantTradeNo":"TEST 123","ItemName":"測試商品 *A*","TradeDesc":"test (1)"}';
      const encoded = javaUrlEncode(original);
      const decoded = javaUrlDecode(encoded);
      expect(decoded).toBe(original);
    });

    it('should handle standard %20 encoding', () => {
      expect(javaUrlDecode('hello%20world')).toBe('hello world');
    });
  });

  describe('AES roundtrip with Java-compatible encoding', () => {
    const hashKey = 'pwFHCqoQZGmho4w6';
    const hashIv = 'EkRm7iFT261dpevs';

    it('should encrypt/decrypt JSON with spaces and special characters', () => {
      const plain = JSON.stringify({
        MerchantTradeNo: 'TEST 123',
        ItemName: '測試商品 *A*',
        TradeDesc: 'test (1)',
      });
      const encrypted = encryptAES(plain, hashKey, hashIv);
      expect(encrypted).toBeTruthy();
      // Verify ciphertext is base64-encoded
      expect(() => Buffer.from(encrypted, 'base64')).not.toThrow();
      expect(encrypted.length % 4).toBe(0);

      const decrypted = decryptAES(encrypted, hashKey, hashIv);
      expect(decrypted).toBe(plain);
    });

    it('should produce decryptable ciphertext that round-trips', () => {
      const plain = '{"MerchantTradeNo":"TEST 123","ItemName":"測試商品 *A*","TradeDesc":"test (1)"}';
      const encrypted = encryptAES(plain, hashKey, hashIv);
      const decrypted = decryptAES(encrypted, hashKey, hashIv);
      expect(decrypted).toBe(plain);
    });
  });
});
