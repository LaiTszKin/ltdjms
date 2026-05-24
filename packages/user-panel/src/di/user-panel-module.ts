import { container, TOKENS } from '@ltdjms/shared';
import type { DomainEventPublisher, DiscordRuntimeGateway, CacheService } from '@ltdjms/shared';
import type {
  BalanceService,
  CurrencyTransactionService,
} from '@ltdjms/economy';
import { ECONOMY_TOKENS } from '@ltdjms/economy';
import type {
  GameTokenService,
  GameTokenTransactionService,
} from '@ltdjms/games';
import { GAMES_TOKENS } from '@ltdjms/games';
import type { RedemptionService, RedemptionTransactionService } from '@ltdjms/shop';
import { SHOP_TOKENS } from '@ltdjms/shop';

import { MemberInfoFacade } from '../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { UserPanelEmbedBuilder } from '../services/UserPanelEmbedBuilder.js';
import { UserPanelCommand } from '../commands/UserPanelCommand.js';
import { RedeemCodeCommandHandler } from '../commands/RedeemCodeCommandHandler.js';
import { TransactionHistoryHandler } from '../handlers/TransactionHistoryHandler.js';
import { RedemptionCodeHandler } from '../handlers/RedemptionCodeHandler.js';
import { UserPanelUpdateListener } from '../listeners/UserPanelUpdateListener.js';

/** Module-level handler reference for DomainEventPublisher unregister support. */
let _userUpdateHandler: ((event: unknown) => void) | null = null;

/**
 * DI tokens for the user panel module.
 */
export const USER_PANEL_TOKENS = {
  MemberInfoFacade: Symbol('MemberInfoFacade'),
  PanelSessionManager: Symbol('PanelSessionManager'),
  UserPanelEmbedBuilder: Symbol('UserPanelEmbedBuilder'),
  UserPanelCommand: Symbol('UserPanelCommand'),
  TransactionHistoryHandler: Symbol('TransactionHistoryHandler'),
  RedemptionCodeHandler: Symbol('RedemptionCodeHandler'),
  RedeemCodeCommandHandler: Symbol('RedeemCodeCommandHandler'),
  UserPanelUpdateListener: Symbol('UserPanelUpdateListener'),
};

/**
 * Initializes the DI container with user panel services, handlers, and listeners.
 * Call after configureShopContainer().
 */
export function configureUserPanelContainer(): void {
  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);

  let cacheService: CacheService | undefined;
  try {
    cacheService = container.resolve<CacheService>(TOKENS.CacheService);
  } catch {
    // CacheService not registered; session manager will use in-memory storage only
  }

  const panelSessionManager = new PanelSessionManager(cacheService);
  container.registerInstance(USER_PANEL_TOKENS.PanelSessionManager, panelSessionManager);
  panelSessionManager.startCleanupInterval();

  const userPanelEmbedBuilder = new UserPanelEmbedBuilder();
  container.registerInstance(USER_PANEL_TOKENS.UserPanelEmbedBuilder, userPanelEmbedBuilder);

  const balanceService = container.resolve<BalanceService>(ECONOMY_TOKENS.BalanceService);
  const gameTokenService = container.resolve<GameTokenService>(GAMES_TOKENS.GameTokenService as symbol);
  const currencyTxService = container.resolve<CurrencyTransactionService>(
    ECONOMY_TOKENS.CurrencyTransactionService,
  );
  const gameTokenTxService = container.resolve<GameTokenTransactionService>(
    GAMES_TOKENS.GameTokenTransactionService as symbol,
  );
  const redemptionService = container.resolve<RedemptionService>(SHOP_TOKENS.RedemptionService);

  let redemptionTxService: RedemptionTransactionService | undefined;
  try {
    redemptionTxService = container.resolve<RedemptionTransactionService>(
      SHOP_TOKENS.RedemptionTransactionService,
    );
  } catch {
    // RedemptionTransactionService not available
  }

  const memberInfoFacade = new MemberInfoFacade(
    balanceService,
    gameTokenService,
    currencyTxService,
    gameTokenTxService,
    redemptionService,
    redemptionTxService,
  );
  container.registerInstance(USER_PANEL_TOKENS.MemberInfoFacade, memberInfoFacade);

  const userPanelCommand = new UserPanelCommand(
    memberInfoFacade,
    panelSessionManager,
    userPanelEmbedBuilder,
  );
  container.registerInstance(USER_PANEL_TOKENS.UserPanelCommand, userPanelCommand);

  const txHistoryHandler = new TransactionHistoryHandler(memberInfoFacade, panelSessionManager);
  container.registerInstance(USER_PANEL_TOKENS.TransactionHistoryHandler, txHistoryHandler);

  const redeemHandler = new RedemptionCodeHandler(memberInfoFacade, panelSessionManager);
  container.registerInstance(USER_PANEL_TOKENS.RedemptionCodeHandler, redeemHandler);

  const redeemCmdHandler = new RedeemCodeCommandHandler();
  container.registerInstance(USER_PANEL_TOKENS.RedeemCodeCommandHandler, redeemCmdHandler);

  const discordGateway = container.resolve<DiscordRuntimeGateway>(TOKENS.DiscordRuntimeGateway);

  const userUpdateListener = new UserPanelUpdateListener(
    panelSessionManager,
    memberInfoFacade,
    discordGateway,
    userPanelEmbedBuilder,
  );
  container.registerInstance(USER_PANEL_TOKENS.UserPanelUpdateListener, userUpdateListener);

  _userUpdateHandler = (event: unknown): void => {
    userUpdateListener.onEvent(event as import('@ltdjms/shared').DomainEvent).catch((err: unknown) => {
      console.error('[UserPanelUpdateListener] Error:', err);
    });
  };
  eventPublisher.register(_userUpdateHandler);
}

/**
 * Disposes user panel module resources. Should be called during application shutdown.
 */
export function disposeUserPanelContainer(): void {
  try {
    const mgr = container.resolve<PanelSessionManager>(USER_PANEL_TOKENS.PanelSessionManager);
    mgr.stopCleanupInterval();
  } catch {
    // Session manager not registered
  }

  try {
    const publisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
    if (_userUpdateHandler) {
      publisher.unregister(_userUpdateHandler);
      _userUpdateHandler = null;
    }
  } catch {
    // DomainEventPublisher not available
  }
}
