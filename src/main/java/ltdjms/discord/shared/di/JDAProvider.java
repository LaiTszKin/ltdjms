package ltdjms.discord.shared.di;

import java.util.concurrent.atomic.AtomicReference;

import net.dv8tion.jda.api.JDA;

/**
 * JDA 實例提供者。
 *
 * <p>用於在 Dagger 無法直接提供 JDA 實例的情況下，延遲設置 JDA。 這允許在 JDA 初始化後，將實例注入到需要它的服務中。
 */
public final class JDAProvider {

  private static final AtomicReference<JDA> jdaRef = new AtomicReference<>();

  private JDAProvider() {
    // 防止實例化
  }

  /**
   * 設置 JDA 實例。
   *
   * @param jda JDA 實例
   */
  public static void setJda(JDA jda) {
    jdaRef.set(jda);
  }

  /**
   * 獲取 JDA 實例。
   *
   * @return JDA 實例
   * @throws IllegalStateException 如果 JDA 尚未設置
   */
  public static JDA getJda() {
    JDA jda = jdaRef.get();
    if (jda == null) {
      throw new IllegalStateException("JDA 實例尚未設置");
    }
    return jda;
  }

  /** 清除 JDA 實例（主要用於測試）。 */
  public static void clear() {
    jdaRef.set(null);
  }
}
