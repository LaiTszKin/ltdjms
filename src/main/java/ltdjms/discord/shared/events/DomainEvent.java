package ltdjms.discord.shared.events;

/**
 * Base interface for domain events. All events must have a guild ID to identify the server context.
 */
public sealed interface DomainEvent
    permits BalanceChangedEvent,
        GameTokenChangedEvent,
        CurrencyConfigChangedEvent,
        DiceGameConfigChangedEvent,
        ProductChangedEvent,
        RedemptionCodesGeneratedEvent,
        ProductRedemptionCompletedEvent,
        AIMessageEvent,
        AIAgentChannelConfigChangedEvent,
        AgentCompletedEvent,
        AgentFailedEvent,
        LangChain4jToolExecutedEvent {
  /**
   * @return the Discord guild ID where the event occurred
   */
  long guildId();
}
