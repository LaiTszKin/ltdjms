/**
 * 護航派單訂單編號產生器。
 * 格式：ESC-YYYYMMDD-XXXXXX（6 位英數字尾碼，排除 I、O、0、1 等混淆字元）
 */
export declare class EscortDispatchOrderNumberGenerator {
    private static readonly PREFIX;
    private static readonly SUFFIX_LENGTH;
    private static readonly ALPHANUMERIC;
    private readonly clock;
    private readonly alphanumeric;
    constructor(clock?: () => number);
    /** 產生一組訂單編號。 */
    generate(): string;
    private randomSuffix;
}
