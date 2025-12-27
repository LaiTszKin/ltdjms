package ltdjms.discord.shared.events;

import java.time.Instant;

import ltdjms.discord.redemption.domain.ProductRedemptionTransaction;

/**
 * 商品兌換完成後發布的事件，用於觸發面板即時更新。
 *
 * @param guildId 伺服器 ID
 * @param userId 使用者 ID
 * @param transaction 商品兌換交易紀錄
 * @param timestamp 事件時間戳
 */
public record ProductRedemptionCompletedEvent(
    long guildId, long userId, ProductRedemptionTransaction transaction, Instant timestamp)
    implements DomainEvent {}
