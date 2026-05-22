import crypto from 'node:crypto';
import { javaUrlEncode, javaUrlDecode } from './url-encoder.js';

/**
 * Select the AES-CBC algorithm name based on key length.
 * Matches Java SecretKeySpec behavior which reads the key length
 * and selects AES-128, AES-192, or AES-256 accordingly.
 *
 * @param key - The AES key string.
 * @returns The Node.js cipher algorithm name ('aes-128-cbc', 'aes-192-cbc', or 'aes-256-cbc').
 * @throws If the key length is not 16, 24, or 32 bytes.
 */
function getCipherName(key: string): string {
  const keyLen = Buffer.from(key, 'utf-8').length;
  if (keyLen === 16) return 'aes-128-cbc';
  if (keyLen === 24) return 'aes-192-cbc';
  if (keyLen === 32) return 'aes-256-cbc';
  throw new Error(`Invalid AES key length: ${keyLen} bytes. Expected 16, 24, or 32 bytes.`);
}

/**
 * AES-CBC encrypt then Base64 encode.
 * 1. Java URL-encode the plain JSON
 * 2. AES-CBC encrypt (Node.js PKCS7 = Java PKCS5 compatible)
 * 3. Base64 encode
 *
 * The cipher algorithm (AES-128/192/256-CBC) is selected dynamically
 * based on hashKey length, matching Java SecretKeySpec behavior.
 */
export function encryptAES(plainJson: string, hashKey: string, hashIv: string): string {
  const urlEncoded = javaUrlEncode(plainJson);
  const cipher = crypto.createCipheriv(
    getCipherName(hashKey),
    Buffer.from(hashKey, 'utf-8'),
    Buffer.from(hashIv, 'utf-8'),
  );
  const encrypted = Buffer.concat([cipher.update(urlEncoded, 'utf-8'), cipher.final()]);
  return encrypted.toString('base64');
}

/**
 * Base64 decode then AES-CBC decrypt then URL-decode.
 * Uses javaUrlDecode to match Java URLDecoder.decode() exactly.
 *
 * The cipher algorithm (AES-128/192/256-CBC) is selected dynamically
 * based on hashKey length, matching Java SecretKeySpec behavior.
 */
export function decryptAES(encryptedBase64: string, hashKey: string, hashIv: string): string {
  const decipher = crypto.createDecipheriv(
    getCipherName(hashKey),
    Buffer.from(hashKey, 'utf-8'),
    Buffer.from(hashIv, 'utf-8'),
  );
  const decrypted = Buffer.concat([
    decipher.update(Buffer.from(encryptedBase64, 'base64')),
    decipher.final(),
  ]);
  const decoded = decrypted.toString('utf-8');
  return javaUrlDecode(decoded);
}
