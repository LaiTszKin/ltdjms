import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
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
  registerUserPanelHandlers,
} from '../user-panel-module.js';
import { USER_PANEL_TOKENS } from '../../testing/index.js';
import type { UserPanelCommand } from '../../commands/UserPanelCommand.js';

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

function registerEconomyShopMocks(): void {
  container.registerInstance(ECONOMY_TOKENS.BalanceService, {
    getBalanceUnchecked: async () => ({
      guildId: 1,
      userId: '1',
      balance: 0,
      currencyName: '金幣',
      currencyIcon: '🪙',
    }),
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
    getBalance: async () => 0,
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
}

/** IT-101: user-panel DI wiring resolves all tokens */
describe('configureUserPanelContainer (IT-101)', () => {
  beforeEach(() => {
    container.clearInstances();
    initializeContainer({
      eventPublisher: new DomainEventPublisher(),
      runtimeGateway: mockGateway,
    });
    registerEconomyShopMocks();
    configureUserPanelContainer();
  });

  afterEach(() => {
    disposeUserPanelContainer();
    container.clearInstances();
  });

  it('should resolve all USER_PANEL_TOKENS', () => {
    expect(container.resolve(USER_PANEL_TOKENS.MemberInfoFacade)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.PanelSessionManager)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.UserPanelEmbedBuilder)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.UserPanelService)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.UserPanelCommand)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.UserPanelButtonHandler)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.RedeemCodeCommandHandler)).toBeDefined();
    expect(container.resolve(USER_PANEL_TOKENS.UserPanelUpdateListener)).toBeDefined();
    expect(container.resolve(TOKENS.DomainEventPublisher)).toBeDefined();
  });

  it('should be idempotent when configureUserPanelContainer is called twice', () => {
    const publisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
    const listenerCountBefore = publisher.listenerCount();

    configureUserPanelContainer();

    expect(publisher.listenerCount()).toBe(listenerCountBefore);
  });

  it('should require configureUserPanelContainer before registerUserPanelHandlers', () => {
    disposeUserPanelContainer();
    expect(() =>
      registerUserPanelHandlers({
        registerCommand: vi.fn(),
        registerInteractionHandler: vi.fn(),
      }),
    ).toThrow('configureUserPanelContainer() must be called before registerUserPanelHandlers()');
  });

  it('should resolve a fresh command instance after dispose and reconfigure', () => {
    const first = container.resolve<UserPanelCommand>(USER_PANEL_TOKENS.UserPanelCommand);

    disposeUserPanelContainer();
    container.clearInstances();
    initializeContainer({
      eventPublisher: new DomainEventPublisher(),
      runtimeGateway: mockGateway,
    });
    registerEconomyShopMocks();
    configureUserPanelContainer();

    const second = container.resolve<UserPanelCommand>(USER_PANEL_TOKENS.UserPanelCommand);
    expect(second).not.toBe(first);
  });
});
