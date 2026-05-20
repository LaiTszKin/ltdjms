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
export declare function buildCheckMacValue(params: Record<string, string>, hashKey: string, hashIv: string): string;
