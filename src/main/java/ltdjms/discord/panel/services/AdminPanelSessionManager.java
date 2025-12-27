package ltdjms.discord.panel.services;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.discord.domain.DiscordSessionManager;
import ltdjms.discord.discord.domain.SessionType;
import ltdjms.discord.discord.services.InteractionSessionManager;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Admin Panel Session 管理器
 *
 * <p>此類別管理管理員面板的 Session，允許面板在建立後被更新。 已重構使用統一的 DiscordSessionManager 抽象介面。
 *
 * <h2>為何需要 Session：</h2>
 *
 * <p>Discord 的互動訊息（尤其是 ephemeral）只能透過 {@link InteractionHook} 在 15 分鐘存活期間內進行編輯。因此必須在 /admin-panel
 * 建立時把 hook 記錄下來， 後續在 Modal 提交時才能安全地更新原本的面板 Embed。
 *
 * <h2>Session 類型：</h2>
 *
 * <p>使用 {@link AdminPanelSessionType#ADMIN_PANEL} 作為 Session 類型。
 *
 * <h2>使用範例：</h2>
 *
 * <pre>{@code
 * // 註冊 Session
 * adminPanelSessionManager.registerSession(guildId, adminId, hook);
 *
 * // 更新面板
 * adminPanelSessionManager.updatePanel(guildId, adminId, hook -> {
 *     hook.editMessageEmbeds(newEmbed).queue();
 * });
 *
 * // 清除 Session
 * adminPanelSessionManager.clearSession(guildId, adminId);
 * }</pre>
 */
public class AdminPanelSessionManager {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPanelSessionManager.class);

  /** Admin Panel 使用的 Session 類型 */
  public enum AdminPanelSessionType implements SessionType {
    ADMIN_PANEL
  }

  private final DiscordSessionManager<AdminPanelSessionType> sessionManager;

  /** 建立一個新的 AdminPanelSessionManager */
  public AdminPanelSessionManager() {
    this.sessionManager = new InteractionSessionManager<>();
  }

  /**
   * 建立一個新的 AdminPanelSessionManager（允許注入 SessionManager 用於測試）
   *
   * @param sessionManager 自訂的 SessionManager
   */
  AdminPanelSessionManager(DiscordSessionManager<AdminPanelSessionType> sessionManager) {
    this.sessionManager = sessionManager;
  }

  /**
   * 註冊一個管理員面板 Session
   *
   * @param guildId Discord Guild ID
   * @param adminId 管理員使用者 ID
   * @param hook InteractionHook
   */
  public void registerSession(long guildId, long adminId, InteractionHook hook) {
    sessionManager.registerSession(
        AdminPanelSessionType.ADMIN_PANEL, guildId, adminId, hook, Map.of() // 無元資料
        );
    LOG.debug("已註冊管理員面板 Session：guildId={}, adminId={}", guildId, adminId);
  }

  /**
   * 更新管理員面板（如果 Session 存在且有效）
   *
   * @param guildId Discord Guild ID
   * @param adminId 管理員使用者 ID
   * @param consumer 對 InteractionHook 執行的操作
   */
  public void updatePanel(long guildId, long adminId, Consumer<InteractionHook> consumer) {
    Optional<DiscordSessionManager.Session<AdminPanelSessionType>> sessionOpt =
        sessionManager.getSession(AdminPanelSessionType.ADMIN_PANEL, guildId, adminId);

    if (sessionOpt.isEmpty()) {
      LOG.debug("管理員面板 Session 不存在或已過期：guildId={}, adminId={}", guildId, adminId);
      return;
    }

    DiscordSessionManager.Session<AdminPanelSessionType> session = sessionOpt.get();
    try {
      consumer.accept(session.hook());
    } catch (Exception e) {
      LOG.warn("更新管理員面板失敗，移除 Session：guildId={}, adminId={}", guildId, adminId, e);
      sessionManager.clearSession(AdminPanelSessionType.ADMIN_PANEL, guildId, adminId);
    }
  }

  /**
   * 清除指定的 Session
   *
   * <p>當管理員關閉面板時應呼叫此方法。
   *
   * @param guildId Discord Guild ID
   * @param adminId 管理員使用者 ID
   */
  public void clearSession(long guildId, long adminId) {
    sessionManager.clearSession(AdminPanelSessionType.ADMIN_PANEL, guildId, adminId);
    LOG.debug("已清除管理員面板 Session：guildId={}, adminId={}", guildId, adminId);
  }

  /**
   * 更新指定 Guild 的所有管理員面板
   *
   * <p>當 Guild 設定變更時使用（例如遊戲配置、貨幣設定、商品變更）。
   *
   * <p>注意：此方法需要底層 SessionManager 支援按 Guild 遍歷。 當前的泛型實作不支援此功能，需要額外實作。
   *
   * @param guildId Discord Guild ID
   * @param consumer 對每個 InteractionHook 執行的操作
   * @deprecated 此方法需要額外實作，建議使用事件驅動的方式更新面板
   */
  @Deprecated
  public void updatePanelsByGuild(long guildId, Consumer<InteractionHook> consumer) {
    // 當前的泛型 SessionManager 不支援按 Guild 遍歷
    // 建議改用 DomainEventPublisher 的更新機制
    LOG.warn("updatePanelsByGuild 方法未實作，建議使用事件驅動的更新機制");
  }

  /**
   * 清除所有過期的 Session
   *
   * <p>建議定期呼叫此方法以釋放記憶體。
   */
  public void clearExpiredSessions() {
    sessionManager.clearExpiredSessions();
    LOG.debug("已清除所有過期的管理員面板 Session");
  }

  /**
   * 檢查指定 Session 是否存在且有效
   *
   * @param guildId Discord Guild ID
   * @param adminId 管理員使用者 ID
   * @return true 如果 Session 存在且未過期
   */
  public boolean hasValidSession(long guildId, long adminId) {
    return sessionManager
        .getSession(AdminPanelSessionType.ADMIN_PANEL, guildId, adminId)
        .isPresent();
  }
}
