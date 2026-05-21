/**
 * Typed representation of the decrypted ECPay callback JSON payload.
 *
 * ECPay sends payment callback data encrypted inside a `Data` field.
 * After AES decryption and JSON.parse, the resulting object conforms
 * to this shape.  All fields are optional because the callback may
 * omit fields depending on the payment stage or configuration.
 */
export interface EcpayCallbackPayload {
  /** Merchant trade number (order identifier). */
  MerchantTradeNo?: string;
  /** Trade status: "1" for paid, "0" for unpaid. */
  TradeStatus?: string;
  /** Response message. */
  RtnMsg?: string;
  /** Merchant ID. */
  MerchantID?: string;
  /** Trade amount. */
  TradeAmt?: number;
  /** ECPay response code (used in log context for unpaid callbacks). */
  RtnCode?: string;
  /** Alternative payment message field. */
  TradeMsg?: string;
  /** Nested order info (ECC format). */
  OrderInfo?: {
    MerchantTradeNo?: string;
    TradeStatus?: string;
    MerchantID?: string;
    TradeAmt?: number;
    TotalAmount?: number;
  };
}
