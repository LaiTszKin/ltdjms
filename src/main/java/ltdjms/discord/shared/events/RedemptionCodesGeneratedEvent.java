package ltdjms.discord.shared.events;

/**
 * 兌換碼批次生成完成後發布的事件，用於觸發面板即時更新。
 *
 * @param guildId 伺服器 ID
 * @param productId 商品 ID
 * @param count 本次生成的兌換碼數量
 */
public record RedemptionCodesGeneratedEvent(long guildId, long productId, int count)
    implements DomainEvent {}
