import { container, TOKENS } from '@ltdjms/shared';
import { EnvironmentConfig } from '@ltdjms/shared';
import type {
  DiscordRuntimeGateway,
  DomainEventPublisher,
  Result,
  DomainError,
} from '@ltdjms/shared';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type pino from 'pino';

import { DrizzleFiatOrderRepository } from '../persistence/drizzle-fiat-order-repository.js';
import { DrizzleProductRepository } from '../persistence/drizzle-product-repository.js';
import { DrizzleRedemptionCodeRepository } from '../persistence/drizzle-redemption-code-repository.js';
import { DrizzleRedemptionTransactionService } from '../persistence/drizzle-redemption-transaction-service.js';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { RedemptionCodeRepository } from '../domain/redemption-code-repository.js';
import type { Product, ProductRepository } from '../domain/product-types.js';
import type { FiatOrder } from '../domain/fiat-order.js';

import { EcpayCvsPaymentService } from '../services/ecpay-cvs-payment.service.js';
import { EcpayTradeQueryService } from '../services/ecpay-trade-query.service.js';
import { FiatPaymentCallbackService } from '../services/fiat-payment-callback.service.js';
import { FiatOrderService } from '../services/fiat-order.service.js';
import { FiatOrderPostPaymentWorker } from '../services/fiat-order-post-payment-worker.js';
import { FiatPaymentReconciliationService } from '../services/fiat-payment-reconciliation.service.js';
import { FiatOrderProcessingScheduler } from '../services/fiat-order-processing-scheduler.js';
import { CurrencyPurchaseService } from '../services/currency-purchase.service.js';
import { ProductService } from '../services/product-service.js';
import { ShopService } from '../services/shop.service.js';
import { ShopCommandHandler } from '../commands/shop-handler.js';
import { RedemptionCodeGenerator } from '../services/redemption-code-generator.js';
import { RedemptionService } from '../services/redemption.service.js';
import { FiatOrderBuyerNotificationService } from '../services/fiat-order-buyer-notification.service.js';
import { EscortOrderBuyerNotificationService } from '../services/escort-order-buyer-notification.service.js';
import { ShopAdminNotificationService } from '../services/shop-admin-notification.service.js';

import { EcpayCallbackHttpServer } from '../web/ecpay-callback-server.js';
import { type EscortDispatchHandoffService } from '../domain/escort-dispatch-handoff-service.js';
import {
  type EscortOrderBuyerNotifier,
  type AdminOrderNotifier,
  type ProductRewardGranter,
} from '../domain/notification-interfaces.js';

// ============================================================
// Third-party service interfaces expected by the shop module
// ============================================================

// ProductRepository is defined in domain/product-types.ts and imported above.

/** Product reward service interface as used by shop services. */
export interface ProductRewardService {
  grantReward(request: {
    guildId: number;
    userId: string;
    product: Product;
    amount: number;
    description: string;
  }): Promise<Result<{ amount: number; currencyBalanceAfter: number | null }, DomainError>>;
}

/** Balance service interface as used by shop services. */
export interface BalanceService {
  tryGetBalance(guildId: number, userId: string): Promise<Result<{ balance: number }, DomainError>>;
}

/** Balance adjustment service interface as used by shop services. */
export interface BalanceAdjustmentService {
  tryAdjustBalance(
    guildId: number,
    userId: string,
    amount: number,
    source?: string,
    description?: string | null,
  ): Promise<Result<{ newBalance: number }, DomainError>>;
}

/** Redemption transaction service interface as used by shop services. */
export interface RedemptionTransactionService {
  recordTransaction(
    guildId: number,
    userId: string,
    product: Product,
    code: { code: string },
  ): Promise<unknown>;

  /** Gets a paginated page of redemption transactions for a user. */
  getUserRedemptionPage(
    guildId: number,
    userId: string,
    page: number,
    pageSize: number,
  ): Promise<{
    items: Array<{
      id: number;
      productName: string;
      code: string;
      rewardAmount: number | null;
      createdAt: Date;
    }>;
    hasNext: boolean;
    totalPages: number;
    currentPage: number;
  }>;
}

/** Configuration options for the shop module container. */
export interface ShopModuleOptions {
  db: NodePgDatabase;
  productRewardService: ProductRewardService;
  escortDispatchHandoffService: EscortDispatchHandoffService;
  balanceService: BalanceService;
  balanceAdjustmentService: BalanceAdjustmentService;
  logger?: pino.Logger;
}

/** Tokens for shop module dependencies. */
export const SHOP_TOKENS = {
  FiatOrderRepository: Symbol('FiatOrderRepository'),
  RedemptionCodeRepository: Symbol('RedemptionCodeRepository'),
  ProductRepository: Symbol('ProductRepository'),
  EcpayCvsPaymentService: Symbol('EcpayCvsPaymentService'),
  EcpayTradeQueryService: Symbol('EcpayTradeQueryService'),
  FiatPaymentCallbackService: Symbol('FiatPaymentCallbackService'),
  FiatOrderService: Symbol('FiatOrderService'),
  FiatOrderPostPaymentWorker: Symbol('FiatOrderPostPaymentWorker'),
  FiatPaymentReconciliationService: Symbol('FiatPaymentReconciliationService'),
  FiatOrderProcessingScheduler: Symbol('FiatOrderProcessingScheduler'),
  ProductService: Symbol('ProductService'),
  CurrencyPurchaseService: Symbol('CurrencyPurchaseService'),
  ShopService: Symbol('ShopService'),
  ShopCommandHandler: Symbol('ShopCommandHandler'),
  RedemptionCodeGenerator: Symbol('RedemptionCodeGenerator'),
  RedemptionService: Symbol('RedemptionService'),
  ShopAdminNotificationService: Symbol('ShopAdminNotificationService'),
  RedemptionTransactionService: Symbol('RedemptionTransactionService'),
  EcpayCallbackHttpServer: Symbol('EcpayCallbackHttpServer'),
};

export function configureContainer(options: ShopModuleOptions): void {
  const config: EnvironmentConfig = container.resolve<EnvironmentConfig>(TOKENS.EnvironmentConfig);
  const discordRuntimeGateway: DiscordRuntimeGateway = container.resolve(
    TOKENS.DiscordRuntimeGateway,
  );
  const eventPublisher: DomainEventPublisher = container.resolve(TOKENS.DomainEventPublisher);
  const log = options.logger ?? container.resolve<pino.Logger>(TOKENS.Logger);

  // ---- Repositories ----
  const fiatOrderRepo = new DrizzleFiatOrderRepository(options.db, log);
  const redemptionCodeRepo = new DrizzleRedemptionCodeRepository(options.db, log);
  const productRepo = new DrizzleProductRepository(options.db);
  const redemptionTxService = new DrizzleRedemptionTransactionService(options.db);

  container.registerInstance<FiatOrderRepository>(SHOP_TOKENS.FiatOrderRepository, fiatOrderRepo);
  container.registerInstance<RedemptionCodeRepository>(
    SHOP_TOKENS.RedemptionCodeRepository,
    redemptionCodeRepo,
  );
  container.registerInstance<ProductRepository>(SHOP_TOKENS.ProductRepository, productRepo);

  // ---- Notification Services ----
  const buyerNotification = new FiatOrderBuyerNotificationService(discordRuntimeGateway, log);
  const escortBuyerNotification = new EscortOrderBuyerNotificationService(
    discordRuntimeGateway,
    log,
  );
  const adminNotification = new ShopAdminNotificationService(discordRuntimeGateway, log);

  container.registerInstance(SHOP_TOKENS.ShopAdminNotificationService, adminNotification);

  // ---- ECPay Services ----
  const ecpayCvsPayment = new EcpayCvsPaymentService(config, log);
  const ecpayTradeQuery = new EcpayTradeQueryService(config, log);

  container.registerInstance(SHOP_TOKENS.EcpayCvsPaymentService, ecpayCvsPayment);
  container.registerInstance(SHOP_TOKENS.EcpayTradeQueryService, ecpayTradeQuery);

  // ---- Callback Service ----
  const paymentCallback = new FiatPaymentCallbackService(config, fiatOrderRepo, log);
  container.registerInstance(SHOP_TOKENS.FiatPaymentCallbackService, paymentCallback);

  // ---- FiatOrder Service ----
  const fiatOrderService = new FiatOrderService(productRepo, ecpayCvsPayment, fiatOrderRepo, log);
  container.registerInstance(SHOP_TOKENS.FiatOrderService, fiatOrderService);

  // ---- Post-Payment Worker ----
  // Type-safe wrappers to avoid unchecked `as unknown as` casts.
  const escortBuyerNotifier: EscortOrderBuyerNotifier = {
    notifyEscortOrderCreated: (dispatchOrder) =>
      escortBuyerNotification.notifyEscortOrderCreated(dispatchOrder),
  };
  const productRewardGranter: ProductRewardGranter = {
    grantReward: async (request) => {
      const result = await options.productRewardService.grantReward(request);
      return {
        isErr: () => result.isErr(),
        getError: () => result.getError(),
      };
    },
  };
  const postPaymentWorker = new FiatOrderPostPaymentWorker(
    fiatOrderRepo,
    buyerNotification,
    options.escortDispatchHandoffService,
    escortBuyerNotifier,
    adminNotification,
    productRewardGranter,
    log,
  );
  container.registerInstance(SHOP_TOKENS.FiatOrderPostPaymentWorker, postPaymentWorker);

  // ---- Reconciliation Service ----
  const reconciliationService = new FiatPaymentReconciliationService(
    fiatOrderRepo,
    ecpayTradeQuery,
    log,
  );
  container.registerInstance(SHOP_TOKENS.FiatPaymentReconciliationService, reconciliationService);

  // ---- Scheduler ----
  const scheduler = new FiatOrderProcessingScheduler(postPaymentWorker, reconciliationService, log);
  container.registerInstance(SHOP_TOKENS.FiatOrderProcessingScheduler, scheduler);

  // ---- Currency Purchase ----
  const currencyPurchase = new CurrencyPurchaseService(
    productRepo,
    options.balanceService,
    options.balanceAdjustmentService,
    options.productRewardService,
    log,
  );
  container.registerInstance(SHOP_TOKENS.CurrencyPurchaseService, currencyPurchase);

  // ---- Product Service ----
  const productService = new ProductService(productRepo, eventPublisher, log);
  container.registerInstance(SHOP_TOKENS.ProductService, productService);

  // ---- Shop Service ----
  const shopService = new ShopService(productRepo, log);
  container.registerInstance(SHOP_TOKENS.ShopService, shopService);

  const shopCommandHandler = new ShopCommandHandler(
    shopService,
    fiatOrderService,
    currencyPurchase,
    productService,
  );
  container.registerInstance(SHOP_TOKENS.ShopCommandHandler, shopCommandHandler);

  // ---- Redemption ----
  const codeGenerator = new RedemptionCodeGenerator();
  const redemptionService = new RedemptionService(
    redemptionCodeRepo,
    productRepo,
    codeGenerator,
    options.productRewardService,
    redemptionTxService,
    eventPublisher,
    log,
  );
  container.registerInstance(SHOP_TOKENS.RedemptionCodeGenerator, codeGenerator);
  container.registerInstance(SHOP_TOKENS.RedemptionService, redemptionService);
  container.registerInstance<RedemptionTransactionService>(
    SHOP_TOKENS.RedemptionTransactionService,
    redemptionTxService,
  );

  // ---- Callback HTTP Server ----
  const callbackServer = new EcpayCallbackHttpServer(config, paymentCallback, log);
  container.registerInstance(SHOP_TOKENS.EcpayCallbackHttpServer, callbackServer);
}
