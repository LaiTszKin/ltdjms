/** Snapshot of a dispatch order used for notification callbacks. */
export interface DispatchOrderSnapshot {
  guildId: number;
  customerUserId: number;
  orderNumber: string;
  sourceProductName?: string | null;
  sourceType?: string | null;
  sourceEscortOptionCode?: string | null;
  sourceCurrencyPrice?: number | null;
  sourceFiatPriceTwd?: number | null;
  sourceReference?: string | null;
}

/** Service interface for auto-creating escort orders from fiat payments. */
export interface EscortDispatchHandoffService {
  handoffFromFiatPayment(
    guildId: number,
    buyerUserId: number,
    product: import('./product-types.js').Product | null,
    sourceReference: string,
  ): Promise<{
    isOk: () => boolean;
    getError: () => { message: string };
    getValue: () => DispatchOrderSnapshot;
  }>;
}
