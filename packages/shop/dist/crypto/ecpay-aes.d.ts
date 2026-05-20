/**
 * AES-128-CBC encrypt then Base64 encode.
 * 1. URL-encode the plain JSON
 * 2. AES-128-CBC encrypt (Node.js PKCS7 = Java PKCS5 compatible)
 * 3. Base64 encode
 *
 * Matches Java EcpayCvsPaymentService.encryptData() exactly.
 */
export declare function encryptAES(plainJson: string, hashKey: string, hashIv: string): string;
/**
 * Base64 decode then AES-128-CBC decrypt then URL-decode.
 * IMPORTANT: Java URLDecoder converts + to space, so we replace + with %20 before decodeURIComponent.
 *
 * Matches Java FiatPaymentCallbackService.decryptData() exactly.
 */
export declare function decryptAES(encryptedBase64: string, hashKey: string, hashIv: string): string;
