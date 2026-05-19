import { container, TOKENS } from '@ltdjms/shared';
import type { EnvironmentConfig, DiscordRuntimeGateway, DomainEventPublisher } from '@ltdjms/shared';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type pino from 'pino';

import { DrizzleFiatOrderRepository } from '../persistence/drizzle-fiat-order-repository.js';
import { DrizzleRedemptionCodeRepository } from '../persistence/drizzle-redemption-code-repository.js';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { RedemptionCodeRepository } from '../domain/redemption-code-repository.js';

import { EcpayCvsPaymentService } from '../services/ecpay-cvs-payment.service.js';
import { EcpayTradeQueryService } from '../services/ecpay-trade-query.service.js';
import { FiatPaymentCallbackService } from '../services/fiat-payment-callback.service.js';
import { FiatOrderService } from '../services/fiat-order.service.js';
import { FiatOrderPostPaymentWorker } from '../services/fiat-order-post-payment-worker.js';
import { FiatPaymentReconciliationService } from '../services/fiat-payment-reconciliation.service.js';
import { FiatOrderProcessingScheduler } from '../services/fiat-order-processing-scheduler.js';
import { CurrencyPurchaseService } from '../services/currency-purchase.service.js';
import { ShopService } from '../services/shop.service.js';
import { RedemptionCodeGenerator } from '../services/redemption-code-generator.js';
import { RedemptionService } from '../services/redemption.service.js';
import { FiatOrderBuyerNotificationService } from '../services/fiat-order-buyer-notification.service.js';
import { EscortOrderBuyerNotificationService } from '../services/escort-order-buyer-notification.service.js';
import { ShopAdminNotificationService } from '../services/shop-admin-notification.service.js';

import { EcpayCallbackHttpServer } from '../web/ecpay-callback-server.js';

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
  RedemptionCodeGenerator: Symbol('RedemptionCodeGenerator'),
  RedemptionService: Symbol('RedemptionService'),
  FiatOrderBuyerNotificationService: Symbol('FiatOrderBuyerNotificationService'),
  EscortOrderBuyerNotificationService: Symbol('EscortOrderBuyerNotificationService'),
  ShopAdminNotificationService: Symbol('ShopAdminNotificationService'),
  EcpayCallbackHttpServer: Symbol('EcpayCallbackHttpServer'),
};

export function configureContainer(options: {
  db: NodePgDatabase;
  productRepository: any;
  productRewardService: any;
  escortDispatchHandoffService: any;
  balanceService: any;
  balanceAdjustmentService: any;
  currencyTransactionService: any;
  redemptionTransactionService: any;
  logger?: pino.Logger;
}): void {
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
  const postPaymentWorker = new FiatOrderPostPaymentWorker(
    fiatOrderRepo,
    buyerNotification,
    options.escortDispatchHandoffService,
    escortBuyerNotification,
    adminNotification,
    options.productRewardService,
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

  // ---- Redemption ----
  const codeGenerator = new RedemptionCodeGenerator();
  const redemptionService = new RedemptionService(
    redemptionCodeRepo,
    options.productRepository,
    codeGenerator,
    options.productRewardService,
    options.redemptionTransactionService,
    eventPublisher,
    log,
  );
  container.registerInstance(SHOP_TOKENS.RedemptionCodeGenerator, codeGenerator);
  container.registerInstance(SHOP_TOKENS.RedemptionService, redemptionService);

  // ---- Callback HTTP Server ----
  const callbackServer = new EcpayCallbackHttpServer(config, paymentCallback, log);
  container.registerInstance(SHOP_TOKENS.EcpayCallbackHttpServer, callbackServer);
}
