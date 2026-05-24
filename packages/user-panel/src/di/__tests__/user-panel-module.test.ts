import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  container,
  TOKENS,
  initializeContainer,
  DomainEventPublisher,
  type DiscordRuntimeGateway,
} from '@ltdjms/shared';
import { ECONOMY_TOKENS } from '@ltdjms/economy';
import { GAMES_TOKENS } from '@ltdjms/games';
import { SHOP_TOKENS } from '@ltdjms/shop';
import {
  configureUserPanelContainer,
  disposeUserPanelContainer,
  USER_PANEL_TOKENS,
} from '../user-panel-module.js';
import type { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import type { PanelSessionManager } from '../../session/PanelSessionManager.js';
import type { UserPanelEmbedBuilder } from '../../services/UserPanelEmbedBuilder.js';
import type { UserPanelService } from '../../services/UserPanelService.js';
import type { UserPanelCommand } from '../../commands/UserPanelCommand.js';
import type { UserPanelButtonHandler } from '../../handlers/UserPanelButtonHandler.js';
import type { RedeemCodeCommandHandler } from '../../commands/RedeemCodeCommandHandler.js';
import type { UserPanelUpdateListener } from '../../listeners/UserPanelUpdateListener.js';

const mockGateway: DiscordRuntimeGateway = {
  isReady: () => true,
  publishReady: () => {},
  requireReadyClient: () => ({}),
  findGuild: () => null,
  findGuildChannel: () => null,
  selfUserId: () => 'bot',
  findThreadChannel: () => null,
  sendDM: async () => false,
  isMemberOnline: async () => false,
  retrieveMemberById: async () => false,
};

/** IT-101: user-panel DI wiring resolves all tokens */
describe('configureUserPanelContainer (IT-101)', () => {
  beforeEach(() => {
    container.clearInstances();
    initializeContainer({
      eventPublisher: new DomainEventPublisher(),
      runtimeGateway: mockGateway,
    });

    container.registerInstance(ECONOMY_TOKENS.BalanceService, {
      getBalanceView: async () => ({ balance: 0, currencyName: '金幣', currencyIcon: '🪙' }),
    });
    container.registerInstance(ECONOMY_TOKENS.CurrencyTransactionService, {
      getTransactionPage: async () => ({
        transactions: [],
        currentPage: 1,
        totalPages: 1,
        totalCount: 0,
        pageSize: 10,
      }),
    });
    container.registerInstance(GAMES_TOKENS.GameTokenService as symbol, {
      getTokenBalance: async () => 0,
    });
    container.registerInstance(GAMES_TOKENS.GameTokenTransactionService as symbol, {
      getTransactionPage: async () => ({
        transactions: [],
        currentPage: 1,
        totalPages: 1,
        totalCount: 0,
        pageSize: 10,
      }),
    });
    container.registerInstance(SHOP_TOKENS.RedemptionService, {
      redeemCode: async () => ({ isErr: () => true, getError: () => new Error('mock') }),
    });

    configureUserPanelContainer();
  });

  afterEach(() => {
    disposeUserPanelContainer();
    container.clearInstances();
  });

  it('should resolve all USER_PANEL_TOKENS', () => {
    expect(container.resolve<MemberInfoFacade>(USER_PANEL_TOKENS.MemberInfoFacade)).toBeDefined();
    expect(
      container.resolve<PanelSessionManager>(USER_PANEL_TOKENS.PanelSessionManager),
    ).toBeDefined();
    expect(
      container.resolve<UserPanelEmbedBuilder>(USER_PANEL_TOKENS.UserPanelEmbedBuilder),
    ).toBeDefined();
    expect(container.resolve<UserPanelService>(USER_PANEL_TOKENS.UserPanelService)).toBeDefined();
    expect(container.resolve<UserPanelCommand>(USER_PANEL_TOKENS.UserPanelCommand)).toBeDefined();
    expect(
      container.resolve<UserPanelButtonHandler>(USER_PANEL_TOKENS.UserPanelButtonHandler),
    ).toBeDefined();
    expect(
      container.resolve<RedeemCodeCommandHandler>(USER_PANEL_TOKENS.RedeemCodeCommandHandler),
    ).toBeDefined();
    expect(
      container.resolve<UserPanelUpdateListener>(USER_PANEL_TOKENS.UserPanelUpdateListener),
    ).toBeDefined();
    expect(container.resolve(TOKENS.DomainEventPublisher)).toBeDefined();
  });

  it('should be idempotent when configureUserPanelContainer is called twice', () => {
    const publisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
    const listenerCountBefore = publisher.listenerCount();

    configureUserPanelContainer();

    expect(publisher.listenerCount()).toBe(listenerCountBefore);
  });
});
