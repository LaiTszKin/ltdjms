import crypto from 'node:crypto';
/**
 * AES-128-CBC encrypt then Base64 encode.
 * 1. URL-encode the plain JSON
 * 2. AES-128-CBC encrypt (Node.js PKCS7 = Java PKCS5 compatible)
 * 3. Base64 encode
 *
 * Matches Java EcpayCvsPaymentService.encryptData() exactly.
 */
export function encryptAES(plainJson, hashKey, hashIv) {
    const urlEncoded = encodeURIComponent(plainJson);
    const cipher = crypto.createCipheriv('aes-128-cbc', Buffer.from(hashKey, 'utf-8'), Buffer.from(hashIv, 'utf-8'));
    const encrypted = Buffer.concat([
        cipher.update(urlEncoded, 'utf-8'),
        cipher.final(),
    ]);
    return encrypted.toString('base64');
}
/**
 * Base64 decode then AES-128-CBC decrypt then URL-decode.
 * IMPORTANT: Java URLDecoder converts + to space, so we replace + with %20 before decodeURIComponent.
 *
 * Matches Java FiatPaymentCallbackService.decryptData() exactly.
 */
export function decryptAES(encryptedBase64, hashKey, hashIv) {
    const decipher = crypto.createDecipheriv('aes-128-cbc', Buffer.from(hashKey, 'utf-8'), Buffer.from(hashIv, 'utf-8'));
    const decrypted = Buffer.concat([
        decipher.update(Buffer.from(encryptedBase64, 'base64')),
        decipher.final(),
    ]);
    // URL decode — Java URLDecoder decodes + to space
    const decoded = decrypted.toString('utf-8');
    return decodeURIComponent(decoded.replace(/\+/g, ' '));
}
//# sourceMappingURL=ecpay-aes.js.map