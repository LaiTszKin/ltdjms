// Type augmentations for packages whose multi-level re-export chains
// cannot be resolved by TypeScript with NodeNext module resolution.
// These augment the module declarations with the exports that are
// present at runtime but invisible to the type checker.

import '@ltdjms/shop';
import '@ltdjms/economy';
import '@ltdjms/ai';
import '@ltdjms/admin';

// ---- @ltdjms/economy ----
declare module '@ltdjms/economy' {
  // CurrencyTransactionSource enum is a runtime value from domain/types.
  // It is reliably exported from the compiled dist; the type augmentation
  // restores the type visibility that NodeNext resolution loses.
  export enum CurrencyTransactionSource {
    ADMIN_ADJUSTMENT = 'ADMIN_ADJUSTMENT',
    DICE_GAME_1_WIN = 'DICE_GAME_1_WIN',
    DICE_GAME_2_WIN = 'DICE_GAME_2_WIN',
    REDEMPTION_CODE = 'REDEMPTION_CODE',
    PRODUCT_REWARD = 'PRODUCT_REWARD',
    PRODUCT_PURCHASE = 'PRODUCT_PURCHASE',
    PRODUCT_PURCHASE_REFUND = 'PRODUCT_PURCHASE_REFUND',
  }
  export interface GameRewardService {
    creditReward(guildId: number, userId: string, amount: number, source: string): Promise<import('@ltdjms/shared').Result<{ newBalance: number }, import('@ltdjms/shared').DomainError>>;
  }
}

// ---- @ltdjms/shop ----
declare module '@ltdjms/shop' {
  export const SHOP_TOKENS: {
    FiatOrderRepository: symbol;
    RedemptionCodeRepository: symbol;
    ProductRepository: symbol;
    EcpayCvsPaymentService: symbol;
    EcpayTradeQueryService: symbol;
    FiatPaymentCallbackService: symbol;
    FiatOrderService: symbol;
    FiatOrderPostPaymentWorker: symbol;
    FiatPaymentReconciliationService: symbol;
    FiatOrderProcessingScheduler: symbol;
    ProductService: symbol;
    CurrencyPurchaseService: symbol;
    ShopService: symbol;
    ShopCommandHandler: symbol;
    RedemptionCodeGenerator: symbol;
    RedemptionService: symbol;
    ShopAdminNotificationService: symbol;
    RedemptionTransactionService: symbol;
    EcpayCallbackHttpServer: symbol;
  };
  export class FiatOrderProcessingScheduler {
    start(): void;
    stop(): void;
  }
  export class EcpayCallbackHttpServer {
    start(): void;
    stop(): Promise<void>;
  }
  export interface ProductRewardService {
    grantReward(request: {
      guildId: number;
      userId: string;
      product: import('@ltdjms/shared').Result;
      amount: number;
      description: string;
    }): Promise<import('@ltdjms/shared').Result<{ amount: number; currencyBalanceAfter: number | null }, import('@ltdjms/shared').DomainError>>;
  }
  export interface BalanceService {
    tryGetBalance(guildId: number, userId: string): Promise<import('@ltdjms/shared').Result<{ balance: number }, import('@ltdjms/shared').DomainError>>;
  }
  export interface BalanceAdjustmentService {
    tryAdjustBalance(guildId: number, userId: string, amount: number, source?: string, description?: string | null): Promise<import('@ltdjms/shared').Result<{ newBalance: number }, import('@ltdjms/shared').DomainError>>;
  }
  export interface EscortDispatchHandoffService {
    handoffFromFiatPayment(guildId: number, buyerUserId: string, fulfillmentProduct: unknown, orderNumber: string): Promise<import('@ltdjms/shared').Result<unknown, import('@ltdjms/shared').DomainError>>;
  }
}

// ---- @ltdjms/ai ----
declare module '@ltdjms/ai' {
  export function disposeAIModule(): Promise<void>;
  export class AIChatMentionListener {
    onMessageCreate(message: unknown): Promise<void>;
  }
}

// ---- @ltdjms/admin ----
declare module '@ltdjms/admin' {
  export function disposeAdminContainer(): void;
}
