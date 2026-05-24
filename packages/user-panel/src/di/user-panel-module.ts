import { type CommandHandler, type InteractionHandler } from '@ltdjms/shared';
import { container, TOKENS } from '@ltdjms/shared';
import type { DomainEventPublisher, DiscordRuntimeGateway } from '@ltdjms/shared';
import type { BalanceService, CurrencyTransactionService } from '@ltdjms/economy';
import { ECONOMY_TOKENS } from '@ltdjms/economy';
import type { GameTokenService, GameTokenTransactionService } from '@ltdjms/games';
import { GAMES_TOKENS } from '@ltdjms/games';
import type { RedemptionService, RedemptionTransactionService } from '@ltdjms/shop';
import { SHOP_TOKENS } from '@ltdjms/shop';

import { MemberInfoFacade } from '../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { UserPanelEmbedBuilder } from '../services/UserPanelEmbedBuilder.js';
import { UserPanelService } from '../services/UserPanelService.js';
import { UserPanelCommand } from '../commands/UserPanelCommand.js';
import { RedeemCodeCommandHandler } from '../commands/RedeemCodeCommandHandler.js';
import { UserPanelButtonHandler } from '../handlers/UserPanelButtonHandler.js';
import { UserPanelUpdateListener } from '../listeners/UserPanelUpdateListener.js';

/** Module-level handler reference for DomainEventPublisher unregister support. */
let _userUpdateHandler: ((event: unknown) => void) | null = null;
let _configured = false;

/**
 * DI tokens for the user panel module.
 */
export const USER_PANEL_TOKENS = {
  MemberInfoFacade: Symbol('MemberInfoFacade'),
  PanelSessionManager: Symbol('PanelSessionManager'),
  UserPanelEmbedBuilder: Symbol('UserPanelEmbedBuilder'),
  UserPanelService: Symbol('UserPanelService'),
  UserPanelCommand: Symbol('UserPanelCommand'),
  UserPanelButtonHandler: Symbol('UserPanelButtonHandler'),
  RedeemCodeCommandHandler: Symbol('RedeemCodeCommandHandler'),
  UserPanelUpdateListener: Symbol('UserPanelUpdateListener'),
};

/**
 * Minimal registrar surface for wiring Discord slash commands and interactions.
 */
export interface UserPanelHandlerRegistrar {
  registerCommand(handler: CommandHandler): void;
  registerInteractionHandler(handler: InteractionHandler): void;
}

/**
 * Initializes the DI container with user panel services, handlers, and listeners.
 * Call after configureShopContainer().
 */
export function configureUserPanelContainer(): void {
  if (_configured) return;
  _configured = true;

  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);

  const panelSessionManager = new PanelSessionManager();
  container.registerInstance(USER_PANEL_TOKENS.PanelSessionManager, panelSessionManager);
  panelSessionManager.startCleanupInterval();

  const userPanelEmbedBuilder = new UserPanelEmbedBuilder();
  container.registerInstance(USER_PANEL_TOKENS.UserPanelEmbedBuilder, userPanelEmbedBuilder);

  const balanceService = container.resolve<BalanceService>(ECONOMY_TOKENS.BalanceService);
  const gameTokenService = container.resolve<GameTokenService>(
    GAMES_TOKENS.GameTokenService as symbol,
  );
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

  const userPanelService = new UserPanelService(memberInfoFacade);
  container.registerInstance(USER_PANEL_TOKENS.UserPanelService, userPanelService);

  const userPanelCommand = new UserPanelCommand(
    userPanelService,
    panelSessionManager,
    userPanelEmbedBuilder,
  );
  container.registerInstance(USER_PANEL_TOKENS.UserPanelCommand, userPanelCommand);

  const userPanelButtonHandler = new UserPanelButtonHandler(userPanelService, panelSessionManager);
  container.registerInstance(USER_PANEL_TOKENS.UserPanelButtonHandler, userPanelButtonHandler);

  const redeemCmdHandler = new RedeemCodeCommandHandler();
  container.registerInstance(USER_PANEL_TOKENS.RedeemCodeCommandHandler, redeemCmdHandler);

  const discordGateway = container.resolve<DiscordRuntimeGateway>(TOKENS.DiscordRuntimeGateway);

  const userUpdateListener = new UserPanelUpdateListener(
    panelSessionManager,
    userPanelService,
    discordGateway,
    userPanelEmbedBuilder,
  );
  container.registerInstance(USER_PANEL_TOKENS.UserPanelUpdateListener, userUpdateListener);

  _userUpdateHandler = (event: unknown): void => {
    userUpdateListener
      .onEvent(event as import('@ltdjms/shared').DomainEvent)
      .catch((err: unknown) => {
        console.error('[UserPanelUpdateListener] Error:', err);
      });
  };
  eventPublisher.register(_userUpdateHandler);
}

/**
 * Registers user panel slash commands and interaction handlers with the shared listener.
 * Call after configureUserPanelContainer() and before listen().
 */
export function registerUserPanelHandlers(registrar: UserPanelHandlerRegistrar): void {
  registrar.registerCommand(
    container.resolve<UserPanelCommand>(USER_PANEL_TOKENS.UserPanelCommand),
  );
  registrar.registerInteractionHandler(
    container.resolve<UserPanelButtonHandler>(USER_PANEL_TOKENS.UserPanelButtonHandler),
  );
  registrar.registerCommand(
    container.resolve<RedeemCodeCommandHandler>(USER_PANEL_TOKENS.RedeemCodeCommandHandler),
  );
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

  _configured = false;
}
