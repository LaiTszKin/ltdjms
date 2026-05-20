import { container, TOKENS } from '@ltdjms/shared';
import { EnvironmentConfig } from '@ltdjms/shared';
import type { DiscordRuntimeGateway, DomainEventPublisher, Result, DomainError } from '@ltdjms/shared';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type pino from 'pino';

import { DrizzleFiatOrderRepository } from '../persistence/drizzle-fiat-order-repository.js';
import { DrizzleRedemptionCodeRepository } from '../persistence/drizzle-redemption-code-repository.js';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { RedemptionCodeRepository } from '../domain/redemption-code-repository.js';
import type { Product } from '../domain/product-types.js';
import type { FiatOrder } from '../domain/fiat-order.js';

import { EcpayCvsPaymentService } from '../services/ecpay-cvs-payment.service.js';
import { EcpayTradeQueryService } from '../services/ecpay-trade-query.service.js';
import { FiatPaymentCallbackService } from '../services/fiat-payment-callback.service.js';
import { FiatOrderService } from '../services/fiat-order.service.js';
import { FiatOrderPostPaymentWorker } from '../services/fiat-order-post-payment-worker.js';
import { FiatPaymentReconciliationService } from '../services/fiat-payment-reconciliation.service.js';
import { FiatOrderProcessingScheduler } from '../services/fiat-order-processing-scheduler.js';
import { CurrencyPurchaseService } from '../services/currency-purchase.service.js';
import { ShopService } from '../services/shop.service.js';
import { ShopCommandHandler } from '../commands/shop-handler.js';
import { RedemptionCodeGenerator } from '../services/redemption-code-generator.js';
import { RedemptionService } from '../services/redemption.service.js';
import { FiatOrderBuyerNotificationService } from '../services/fiat-order-buyer-notification.service.js';
import { EscortOrderBuyerNotificationService } from '../services/escort-order-buyer-notification.service.js';
import { ShopAdminNotificationService } from '../services/shop-admin-notification.service.js';

import { EcpayCallbackHttpServer } from '../web/ecpay-callback-server.js';
import {
  type EscortDispatchHandoffService,
  type EscortOrderBuyerNotifier,
  type AdminOrderNotifier,
  type ProductRewardGranter,
} from '../services/fiat-order-post-payment-worker.js';

// ============================================================
// Third-party service interfaces expected by the shop module
// ============================================================

/** Product repository interface as used by shop services. */
export interface ProductRepository {
  findById(id: number): Promise<Product | null>;
  countByGuildId(guildId: number): Promise<number>;
  findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
  countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
  findByGuildIdAndNameContaining(guildId: number, keyword: string, page: number, size: number): Promise<Product[]>;
}

/** Product reward service interface as used by shop services. */
export interface ProductRewardService {
  grantReward(request: {
    guildId: number;
    userId: number;
    product: Product;
    amount: number;
    description: string;
  }): Promise<Result<{ amount: number; currencyBalanceAfter: number | null; formatReward(product: Product): string }, DomainError>>;
}

/** Balance service interface as used by shop services. */
export interface BalanceService {
  tryGetBalance(guildId: number, userId: number): Promise<Result<{ balance: number }, DomainError>>;
}

/** Balance adjustment service interface as used by shop services. */
export interface BalanceAdjustmentService {
  tryAdjustBalance(guildId: number, userId: number, amount: number): Promise<Result<{ newBalance: number }, DomainError>>;
}

/** Currency transaction service interface as used by shop services. */
export interface CurrencyTransactionService {
  recordTransaction(guildId: number, userId: number, amount: number, balance: number, source: string, description: string): Promise<void>;
}

/** Redemption transaction service interface as used by shop services. */
export interface RedemptionTransactionService {
  recordTransaction(guildId: number, userId: number, product: Product, code: { code: string }): Promise<unknown>;

  /** Gets a paginated page of redemption transactions for a user. */
  getUserRedemptionPage(guildId: number, userId: number, page: number, pageSize: number): Promise<{
    items: Array<{ id: number; productName: string; code: string; rewardedAmount: number | null; createdAt: Date }>;
    hasNext: boolean;
    totalPages: number;
    currentPage: number;
  }>;
}

/** Configuration options for the shop module container. */
export interface ShopModuleOptions {
  db: NodePgDatabase;
  productRepository: ProductRepository;
  productRewardService: ProductRewardService;
  escortDispatchHandoffService: EscortDispatchHandoffService;
  balanceService: BalanceService;
  balanceAdjustmentService: BalanceAdjustmentService;
  currencyTransactionService: CurrencyTransactionService;
  redemptionTransactionService: RedemptionTransactionService;
  logger?: pino.Logger;
}

/** Tokens for shop module dependencies. */
export const SHOP_TOKENS = {
  FiatOrderRepository: Symbol('FiatOrderRepository'),
  RedemptionCodeRepository: Symbol('RedemptionCodeRepository'),
  EcpayCvsPaymentService: Symbol('EcpayCvsPaymentService'),
  EcpayTradeQueryService: Symbol('EcpayTradeQueryService'),
  FiatPaymentCallbackService: Symbol('FiatPaymentCallbackService'),
  FiatOrderService: Symbol('FiatOrderService'),
  FiatOrderPostPaymentWorker: Symbol('FiatOrderPostPaymentWorker'),
  FiatPaymentReconciliationService: Symbol('FiatPaymentReconciliationService'),
  FiatOrderProcessingScheduler: Symbol('FiatOrderProcessingScheduler'),
  CurrencyPurchaseService: Symbol('CurrencyPurchaseService'),
  ShopService: Symbol('ShopService'),
  ShopCommandHandler: Symbol('ShopCommandHandler'),
  RedemptionCodeGenerator: Symbol('RedemptionCodeGenerator'),
  RedemptionService: Symbol('RedemptionService'),
  FiatOrderBuyerNotificationService: Symbol('FiatOrderBuyerNotificationService'),
  EscortOrderBuyerNotificationService: Symbol('EscortOrderBuyerNotificationService'),
  ShopAdminNotificationService: Symbol('ShopAdminNotificationService'),
  RedemptionTransactionService: Symbol('RedemptionTransactionService'),
  EcpayCallbackHttpServer: Symbol('EcpayCallbackHttpServer'),
};

export function configureContainer(options: ShopModuleOptions): void {
  const config: EnvironmentConfig = container.resolve(EnvironmentConfig);
  const discordRuntimeGateway: DiscordRuntimeGateway = container.resolve(TOKENS.DiscordRuntimeGateway);
  const eventPublisher: DomainEventPublisher = container.resolve(TOKENS.DomainEventPublisher);
  const log = options.logger ?? container.resolve<any>('Logger');

  // ---- Repositories ----
  const fiatOrderRepo = new DrizzleFiatOrderRepository(options.db, log);
  const redemptionCodeRepo = new DrizzleRedemptionCodeRepository(options.db, log);

  container.registerInstance<FiatOrderRepository>(SHOP_TOKENS.FiatOrderRepository, fiatOrderRepo);
  container.registerInstance<RedemptionCodeRepository>(
    SHOP_TOKENS.RedemptionCodeRepository,
    redemptionCodeRepo,
  );

  // ---- Notification Services ----
  const buyerNotification = new FiatOrderBuyerNotificationService(discordRuntimeGateway, log);
  const escortBuyerNotification = new EscortOrderBuyerNotificationService(discordRuntimeGateway, log);
  const adminNotification = new ShopAdminNotificationService(discordRuntimeGateway, log);

  container.registerInstance(SHOP_TOKENS.FiatOrderBuyerNotificationService, buyerNotification);
  container.registerInstance(SHOP_TOKENS.EscortOrderBuyerNotificationService, escortBuyerNotification);
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
  const fiatOrderService = new FiatOrderService(
    options.productRepository,
    ecpayCvsPayment,
    fiatOrderRepo,
    log,
  );
  container.registerInstance(SHOP_TOKENS.FiatOrderService, fiatOrderService);

  // ---- Post-Payment Worker ----
  // Cast through unknown because the notification services have parameter-count
  // mismatches between their declared methods and the worker's calling convention.
  const postPaymentWorker = new FiatOrderPostPaymentWorker(
    fiatOrderRepo,
    buyerNotification,
    options.escortDispatchHandoffService,
    escortBuyerNotification as unknown as EscortOrderBuyerNotifier,
    adminNotification,
    options.productRewardService as unknown as ProductRewardGranter,
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
    options.productRepository,
    options.balanceService,
    options.balanceAdjustmentService,
    options.currencyTransactionService,
    options.productRewardService,
    log,
  );
  container.registerInstance(SHOP_TOKENS.CurrencyPurchaseService, currencyPurchase);

  // ---- Shop Service ----
  const shopService = new ShopService(options.productRepository, log);
  container.registerInstance(SHOP_TOKENS.ShopService, shopService);

  const shopCommandHandler = new ShopCommandHandler(shopService, fiatOrderService, currencyPurchase);
  container.registerInstance(SHOP_TOKENS.ShopCommandHandler, shopCommandHandler);

  // ---- Redemption ----
  const codeGenerator = new RedemptionCodeGenerator();
  const redemptionTxService: RedemptionTransactionService = options.redemptionTransactionService;
  const redemptionService = new RedemptionService(
    redemptionCodeRepo,
    options.productRepository,
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
