// Crypto
export { encryptAES, decryptAES } from './crypto/ecpay-aes.js';
export { buildCheckMacValue } from './crypto/ecpay-checkmac.js';

// Domain
export {
  FiatOrderStatus,
  FiatOrderSchema,
  createPending,
  createPendingSimple,
  isPaid,
  isExpired,
  isTerminal,
  isFulfilled,
  isBuyerNotified,
  isRewardGranted,
  isAdminNotified,
  hasFulfillmentReward,
  shouldAutoCreateEscortOrder,
  toFulfillmentProduct,
} from './domain/fiat-order.js';
export type { FiatOrder } from './domain/fiat-order.js';

export type { FiatOrderRepository } from './domain/fiat-order-repository.js';

export {
  RedemptionCodeSchema,
  CODE_LENGTH,
  CODE_CHARACTERS,
  createRedemptionCode,
  withRedeemed,
  isRedeemed,
  isExpired as isRedemptionCodeExpired,
  isValid as isRedemptionCodeValid,
  isInvalidated,
  withInvalidated,
  belongsToGuild,
  getMaskedCode,
} from './domain/redemption-code.js';
export type { RedemptionCode } from './domain/redemption-code.js';

export type { RedemptionCodeRepository, CodeStats } from './domain/redemption-code-repository.js';

export {
  RewardType,
  hasReward,
  formatReward,
  hasCurrencyPrice,
  formatCurrencyPrice,
  hasFiatPriceTwd,
  formatFiatPriceTwd,
  isFiatOnly,
  shouldAutoCreateEscortOrder as shouldProductAutoCreateEscortOrder,
  createProduct,
} from './domain/product-types.js';
export type { Product, ProductRepository } from './domain/product-types.js';

// Persistence
export {
  fiatOrder,
  redemptionCode,
  productRedemptionTransaction,
  product,
} from './persistence/schema.js';
export { DrizzleFiatOrderRepository } from './persistence/drizzle-fiat-order-repository.js';
export { DrizzleRedemptionCodeRepository } from './persistence/drizzle-redemption-code-repository.js';
export { DrizzleProductRepository } from './persistence/drizzle-product-repository.js';

// Services
export { EcpayCvsPaymentService } from './services/ecpay-cvs-payment.service.js';
export type { CvsPaymentCode } from './services/ecpay-cvs-payment.service.js';

export { EcpayTradeQueryService } from './services/ecpay-trade-query.service.js';
export type { QueryTradeResult } from './services/ecpay-trade-query.service.js';

export { FiatPaymentCallbackService, CallbackResult } from './services/fiat-payment-callback.service.js';

export { FiatOrderService, formatFiatOrderDMMessage } from './services/fiat-order.service.js';
export type { FiatOrderResult } from './services/fiat-order.service.js';

export { FiatOrderPostPaymentWorker } from './services/fiat-order-post-payment-worker.js';

export { FiatPaymentReconciliationService } from './services/fiat-payment-reconciliation.service.js';

export { FiatOrderProcessingScheduler } from './services/fiat-order-processing-scheduler.js';

export { CurrencyPurchaseService, formatPurchaseSuccessMessage } from './services/currency-purchase.service.js';
export type { PurchaseResult } from './services/currency-purchase.service.js';

export { ShopService, ShopPageHelper, PAGE_SIZE } from './services/shop.service.js';
export type { ShopPage } from './services/shop.service.js';

export {
  buildShopEmbed,
  buildEmptyShopEmbed,
  buildPaymentMethodChoiceEmbed,
  buildShopComponents,
  buildSearchComponents,
  buildSearchResultEmbed,
  buildPurchaseConfirmEmbed,
  encodeKeyword,
  decodeKeyword,
  BUTTON_PREV_PAGE,
  BUTTON_NEXT_PAGE,
  BUTTON_BUY,
  SELECT_BUY_PRODUCT,
  BUTTON_SEARCH,
  BUTTON_PAY_WITH_CURRENCY,
  BUTTON_PAY_WITH_FIAT,
  BUTTON_BACK_TO_SHOP,
  SELECT_SEARCH_BUY,
  BUTTON_SEARCH_PREV,
  BUTTON_SEARCH_NEXT,
  MODAL_SEARCH,
} from './services/shop-view.js';

export { RedemptionCodeGenerator } from './services/redemption-code-generator.js';

export { RedemptionService, formatRedemptionSuccessMessage } from './services/redemption.service.js';
export type { RedemptionResult, CodePage } from './services/redemption.service.js';

export { FiatOrderBuyerNotificationService } from './services/fiat-order-buyer-notification.service.js';
export { EscortOrderBuyerNotificationService } from './services/escort-order-buyer-notification.service.js';
export { ShopAdminNotificationService } from './services/shop-admin-notification.service.js';

export { ShopCommandHandler } from './commands/index.js';

// Web
export { EcpayCallbackHttpServer } from './web/ecpay-callback-server.js';

// DI
export {
  configureContainer,
  SHOP_TOKENS,
  type ProductRewardService,
  type BalanceService,
  type BalanceAdjustmentService,
  type CurrencyTransactionService,
  type RedemptionTransactionService,
  type ShopModuleOptions,
} from './di/shop-module.js';

export type { EscortDispatchHandoffService } from './services/fiat-order-post-payment-worker.js';

// Persistence (additional)
export { DrizzleRedemptionTransactionService } from './persistence/drizzle-redemption-transaction-service.js';
