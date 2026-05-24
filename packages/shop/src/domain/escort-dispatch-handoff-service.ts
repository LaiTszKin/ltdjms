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

type HandoffResult = {
  isOk: () => boolean;
  getError: () => { message: string };
  getValue: () => DispatchOrderSnapshot;
};

/** Service interface for auto-creating escort orders from shop purchases. */
export interface EscortDispatchHandoffService {
  handoffFromFiatPayment(
    guildId: number,
    buyerUserId: number,
    product: import('./product-types.js').Product | null,
    sourceReference: string,
  ): Promise<HandoffResult>;

  handoffFromCurrencyPurchase(
    guildId: number,
    buyerUserId: number,
    product: import('./product-types.js').Product | null,
    sourceReference: string,
  ): Promise<HandoffResult>;
}
