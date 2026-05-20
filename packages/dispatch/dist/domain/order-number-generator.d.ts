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
/**
 * 嘗試產生一個在資料庫中不重複的訂單編號。
 * @param generator 訂單編號產生器
 * @param existsFn 檢查編號是否已存在的回呼函數
 * @param maxRetries 最大重試次數（預設 20 次）
 * @returns 唯一的訂單編號
 * @throws 若超過最大重試次數仍無法產生唯一編號
 */
export declare function generateUniqueOrderNumber(generator: EscortDispatchOrderNumberGenerator, existsFn: (orderNumber: string) => Promise<boolean>, maxRetries?: number): Promise<string>;
