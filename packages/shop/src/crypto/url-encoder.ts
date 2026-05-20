/**
 * Java URLEncoder.encode() compatible URL encoding.
 * This matches the Java implementation used by ECPay for CheckMacValue and AES encryption.
 */
export function javaUrlEncode(str: string): string {
  // encodeURIComponent doesn't encode * ! ' ( ) ~
  // Java URLEncoder.encode encodes them
  return encodeURIComponent(str)
    .replace(/!/g, '%21')
    .replace(/'/g, '%27')
    .replace(/\(/g, '%28')
    .replace(/\)/g, '%29')
    .replace(/~/g, '%7E')
    // Java URLEncoder.encode encodes space as +, not %20
    .replace(/%20/g, '+');
}

/**
 * Reverse: Java URLDecoder.decode() compatible URL decoding.
 */
export function javaUrlDecode(str: string): string {
  // Java URLDecoder encodes space as +, decodeURIComponent handles %20
  const plusDecoded = str.replace(/\+/g, ' ');
  try {
    return decodeURIComponent(plusDecoded);
  } catch {
    // Some edge cases may still have partially-encoded sequences
    return plusDecoded;
  }
}
