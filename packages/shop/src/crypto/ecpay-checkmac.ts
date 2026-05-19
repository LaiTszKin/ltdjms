import crypto from 'node:crypto';

/**
 * Builds the ECPay CheckMacValue (SHA-256) for a set of parameters.
 *
 * Steps:
 * 1. Sort parameters alphabetically by key, exclude empty/null values
 * 2. Build string: HashKey={key}&{sortedParams}&HashIV={iv}
 * 3. URL-encode the whole string, lowercase
 * 4. Apply ECPay-specific substitutions
 * 5. SHA-256 hash, uppercase hex
 *
 * Matches Java EcpayTradeQueryService.buildCheckMacValue() exactly.
 */
export function buildCheckMacValue(
  params: Record<string, string>,
  hashKey: string,
  hashIv: string,
): string {
  // 1. Sort params alphabetically, exclude empty/null
  const sorted = Object.entries(params)
    .filter(([_, v]) => v !== '' && v !== null && v !== undefined)
    .sort(([a], [b]) => a.localeCompare(b));

  // 2. Build check string
  let checkStr = `HashKey=${hashKey}`;
  for (const [key, value] of sorted) {
    checkStr += `&${key}=${value}`;
  }
  checkStr += `&HashIV=${hashIv}`;

  // 3. URL encode and lowercase (Java style)
  let encoded = encodeURIComponent(checkStr).toLowerCase();

  // 4. ECPay-specific URL encoding substitutions
  const substitutions: [RegExp, string][] = [
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
  for (const [pattern, replacement] of substitutions) {
    encoded = encoded.replace(pattern, replacement);
  }

  // 5. SHA-256 hash and uppercase hex
  const hash = crypto.createHash('sha256').update(encoded).digest('hex');
  return hash.toUpperCase();
}
