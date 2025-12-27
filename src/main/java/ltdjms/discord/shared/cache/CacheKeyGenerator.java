package ltdjms.discord.shared.cache;

/**
 * 緩存鍵生成器介面。
 *
 * <p>提供統一的緩存鍵格式，避免鍵衝突並便於管理。 鍵格式：{namespace}:{guildId}:{entityType}:{entityId}
 */
public interface CacheKeyGenerator {

  /** 鍵的命名空間前綴。 */
  String NAMESPACE = "cache";

  /**
   * 生成貨幣餘額的緩存鍵。
   *
   * @param guildId Discord 公會 ID
   * @param userId Discord 用戶 ID
   * @return 緩存鍵
   */
  String balanceKey(long guildId, long userId);

  /**
   * 生成遊戲代幣的緩存鍵。
   *
   * @param guildId Discord 公會 ID
   * @param userId Discord 用戶 ID
   * @return 緩存鍵
   */
  String gameTokenKey(long guildId, long userId);
}
