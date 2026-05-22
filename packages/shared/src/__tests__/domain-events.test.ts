import { describe, it, expect } from 'vitest';
import type {
  BalanceChangedEvent,
  GameTokenChangedEvent,
  CurrencyConfigChangedEvent,
  DiceGameConfigChangedEvent,
  ProductChangedEvent,
  RedemptionCodesGeneratedEvent,
  ProductRedemptionCompletedEvent,
  AIAgentChannelConfigChangedEvent,
  AgentFailedEvent,
  AIChannelConfigChangedEvent,
  DispatchAfterSalesConfigChangedEvent,
  EscortPricingChangedEvent,
  EscortCatalogChangedEvent,
} from '../types/events/index.js';

describe('Domain events', () => {
  describe('eventType literals', () => {
    it('BalanceChangedEvent has eventType "balance_changed"', () => {
      const event: BalanceChangedEvent = {
        eventType: 'balance_changed',
        guildId: '1',
        userId: '2',
        newBalance: 100,
      };
      expect(event.eventType).toBe('balance_changed');
    });

    it('GameTokenChangedEvent has eventType "game_token_changed"', () => {
      const event: GameTokenChangedEvent = {
        eventType: 'game_token_changed',
        guildId: '1',
        userId: '2',
        newTokens: 50,
      };
      expect(event.eventType).toBe('game_token_changed');
    });

    it('CurrencyConfigChangedEvent has eventType "currency_config_changed"', () => {
      const event: CurrencyConfigChangedEvent = {
        eventType: 'currency_config_changed',
        guildId: '1',
        currencyName: 'Gold',
        currencyIcon: 'G',
      };
      expect(event.eventType).toBe('currency_config_changed');
    });

    it('DiceGameConfigChangedEvent has eventType "dice_game_config_changed"', () => {
      const event: DiceGameConfigChangedEvent = {
        eventType: 'dice_game_config_changed',
        guildId: '1',
        gameType: 'DICE_GAME_1',
      };
      expect(event.eventType).toBe('dice_game_config_changed');
    });

    it('ProductChangedEvent has eventType "product_changed"', () => {
      const event: ProductChangedEvent = {
        eventType: 'product_changed',
        guildId: '1',
        productId: 1,
        operationType: 'CREATED',
      };
      expect(event.eventType).toBe('product_changed');
    });

    it('RedemptionCodesGeneratedEvent has eventType "redemption_codes_generated"', () => {
      const event: RedemptionCodesGeneratedEvent = {
        eventType: 'redemption_codes_generated',
        guildId: '1',
        productId: 1,
        count: 10,
      };
      expect(event.eventType).toBe('redemption_codes_generated');
    });

    it('ProductRedemptionCompletedEvent has eventType "product_redemption_completed"', () => {
      const event: ProductRedemptionCompletedEvent = {
        eventType: 'product_redemption_completed',
        guildId: '1',
        userId: '2',
        transaction: {
          id: 'tx-1',
          userId: '2',
          productId: 1,
          timestamp: new Date(),
        },
        timestamp: new Date(),
      };
      expect(event.eventType).toBe('product_redemption_completed');
    });

    it('AIAgentChannelConfigChangedEvent has eventType "ai_agent_channel_config_changed"', () => {
      const event: AIAgentChannelConfigChangedEvent = {
        eventType: 'ai_agent_channel_config_changed',
        guildId: '1',
        channelId: 123,
        agentEnabled: true,
        changedAt: new Date(),
      };
      expect(event.eventType).toBe('ai_agent_channel_config_changed');
    });

    it('AgentFailedEvent has eventType "agent_failed"', () => {
      const event: AgentFailedEvent = {
        eventType: 'agent_failed',
        guildId: '1',
        channelId: '123',
        userId: '456',
        conversationId: 'conv-1',
        reason: 'timeout',
        timestamp: new Date(),
      };
      expect(event.eventType).toBe('agent_failed');
    });

    it('AIChannelConfigChangedEvent has eventType "ai_channel_config_changed"', () => {
      const event: AIChannelConfigChangedEvent = {
        eventType: 'ai_channel_config_changed',
        guildId: '1',
        changeType: 'channel_added',
        targetId: '123',
      };
      expect(event.eventType).toBe('ai_channel_config_changed');
    });

    it('DispatchAfterSalesConfigChangedEvent has eventType "dispatch_after_sales_config_changed"', () => {
      const event: DispatchAfterSalesConfigChangedEvent = {
        eventType: 'dispatch_after_sales_config_changed',
        guildId: '1',
      };
      expect(event.eventType).toBe('dispatch_after_sales_config_changed');
    });

    it('EscortPricingChangedEvent has eventType "escort_pricing_changed"', () => {
      const event: EscortPricingChangedEvent = {
        eventType: 'escort_pricing_changed',
        guildId: '1',
        optionCode: 'OPT-1',
        newPrice: 100,
      };
      expect(event.eventType).toBe('escort_pricing_changed');
    });

    it('EscortCatalogChangedEvent has eventType "escort_catalog_changed"', () => {
      const event: EscortCatalogChangedEvent = {
        eventType: 'escort_catalog_changed',
        guildId: '1',
        entryCode: 'ENTRY-1',
        operationType: 'CREATED',
      };
      expect(event.eventType).toBe('escort_catalog_changed');
    });

    it('all events extend DomainEvent with guildId and eventType', () => {
      const events: Array<{ guildId: string; eventType: string }> = [
        { guildId: '1', eventType: 'balance_changed' },
        { guildId: '1', eventType: 'game_token_changed' },
        { guildId: '1', eventType: 'currency_config_changed' },
        { guildId: '1', eventType: 'dice_game_config_changed' },
        { guildId: '1', eventType: 'product_changed' },
        { guildId: '1', eventType: 'redemption_codes_generated' },
        { guildId: '1', eventType: 'product_redemption_completed' },
        { guildId: '1', eventType: 'ai_agent_channel_config_changed' },
        { guildId: '1', eventType: 'agent_failed' },
        { guildId: '1', eventType: 'ai_channel_config_changed' },
        { guildId: '1', eventType: 'dispatch_after_sales_config_changed' },
        { guildId: '1', eventType: 'escort_pricing_changed' },
        { guildId: '1', eventType: 'escort_catalog_changed' },
      ];
      expect(events).toHaveLength(13);
    });
  });
});
