package ltdjms.discord.shared.cache;

/**
 * 預設的緩存鍵生成器實作。
 *
 * <p>鍵格式：
 *
 * <ul>
 *   <li>貨幣餘額：cache:balance:{guildId}:{userId}
 *   <li>遊戲代幣：cache:gametoken:{guildId}:{userId}
 * </ul>
 */
public class DefaultCacheKeyGenerator implements CacheKeyGenerator {

  private static final String ENTITY_TYPE_BALANCE = "balance";
  private static final String ENTITY_TYPE_GAME_TOKEN = "gametoken";

  @Override
  public String balanceKey(long guildId, long userId) {
    return String.format("%s:%s:%d:%d", NAMESPACE, ENTITY_TYPE_BALANCE, guildId, userId);
  }

  @Override
  public String gameTokenKey(long guildId, long userId) {
    return String.format("%s:%s:%d:%d", NAMESPACE, ENTITY_TYPE_GAME_TOKEN, guildId, userId);
  }
}
