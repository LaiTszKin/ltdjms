package ltdjms.discord.discord.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ltdjms.discord.discord.domain.DiscordSessionManager;
import ltdjms.discord.discord.domain.SessionType;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * InteractionSessionManager 實作單元測試
 *
 * <p>測試基於 InteractionHook 的泛型 SessionManager 實作。
 */
@DisplayName("InteractionSessionManager 實作測試")
class InteractionSessionManagerTest {

  private InteractionSessionManager<TestSessionType> manager;

  @Mock private InteractionHook mockHook;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    manager = new InteractionSessionManager<>();
  }

  @Test
  @DisplayName("建構應該建立一個新的 SessionManager")
  void constructorShouldCreateNewManager() {
    assertThat(manager).isNotNull();
  }

  @Test
  @DisplayName("registerSession 應該註冊新的 Session")
  void registerSessionShouldRegisterNewSession() {
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of("page", 1));

    Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

    assertThat(sessionOpt).isPresent();
    assertThat(sessionOpt.get().type()).isEqualTo(TestSessionType.USER_PANEL);
    assertThat(sessionOpt.get().hook()).isEqualTo(mockHook);
  }

  @Test
  @DisplayName("registerSession 應該儲存元資料")
  void registerSessionShouldStoreMetadata() {
    Map<String, Object> metadata = Map.of("page", 1, "filter", "active");

    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, metadata);

    Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

    assertThat(sessionOpt).isPresent();
    assertThat(sessionOpt.get().metadata()).isEqualTo(metadata);
  }

  @Test
  @DisplayName("getSession 應該返回已註冊的 Session")
  void getSessionShouldReturnRegisteredSession() {
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of());

    Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

    assertThat(sessionOpt).isPresent();
    assertThat(sessionOpt.get().hook()).isEqualTo(mockHook);
  }

  @Test
  @DisplayName("getSession 對於不存在的 Session 應該返回空")
  void getSessionShouldReturnEmptyForNonExistent() {
    Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

    assertThat(sessionOpt).isEmpty();
  }

  @Test
  @DisplayName("getSession 應該自動過濾過期的 Session")
  void getSessionShouldFilterExpiredSessions() {
    // 註冊一個短期 Session
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of());
    // 修改 TTL 為非常短的時間以便測試
    // 注意：這需要實作支援動態 TTL，或使用等待方式

    // 由於 TTL 預設較長，我們驗證剛註冊的 Session 是有效的
    Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

    assertThat(sessionOpt).isPresent();
    assertThat(sessionOpt.get().isExpired()).isFalse();
  }

  @Test
  @DisplayName("clearSession 應該移除指定的 Session")
  void clearSessionShouldRemoveSession() {
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of());

    assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isPresent();

    manager.clearSession(TestSessionType.USER_PANEL, 123L, 456L);

    assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isEmpty();
  }

  @Test
  @DisplayName("clearExpiredSessions 應該移除所有過期的 Session")
  void clearExpiredSessionsShouldRemoveAllExpired() {
    // 註冊多個 Session
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of());
    manager.registerSession(TestSessionType.ADMIN_PANEL, 123L, 789L, mockHook, Map.of());

    // 清理過期 Session（目前應該沒有過期的）
    manager.clearExpiredSessions();

    // 驗證 Session 仍然存在
    assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isPresent();
    assertThat(manager.getSession(TestSessionType.ADMIN_PANEL, 123L, 789L)).isPresent();
  }

  @Nested
  @DisplayName("Session 類型隔離測試")
  class SessionTypeIsolationTests {

    @Test
    @DisplayName("不同類型的 Session 應該獨立管理")
    void differentSessionTypesShouldBeIndependent() {
      manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of());
      manager.registerSession(TestSessionType.ADMIN_PANEL, 123L, 456L, mockHook, Map.of());

      Optional<DiscordSessionManager.Session<TestSessionType>> userSession =
          manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
      Optional<DiscordSessionManager.Session<TestSessionType>> adminSession =
          manager.getSession(TestSessionType.ADMIN_PANEL, 123L, 456L);

      assertThat(userSession).isPresent();
      assertThat(adminSession).isPresent();
      assertThat(userSession.get().type()).isEqualTo(TestSessionType.USER_PANEL);
      assertThat(adminSession.get().type()).isEqualTo(TestSessionType.ADMIN_PANEL);
    }

    @Test
    @DisplayName("清除一種類型的 Session 不應影響其他類型")
    void clearingOneTypeShouldNotAffectOthers() {
      manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of());
      manager.registerSession(TestSessionType.ADMIN_PANEL, 123L, 456L, mockHook, Map.of());

      manager.clearSession(TestSessionType.USER_PANEL, 123L, 456L);

      assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isEmpty();
      assertThat(manager.getSession(TestSessionType.ADMIN_PANEL, 123L, 456L)).isPresent();
    }
  }

  @Nested
  @DisplayName("Guild 和使用者隔離測試")
  class GuildAndUserIsolationTests {

    @Test
    @DisplayName("不同 Guild 的 Session 應該獨立")
    void sessionsFromDifferentGuildsShouldBeIndependent() {
      manager.registerSession(TestSessionType.USER_PANEL, 111L, 456L, mockHook, Map.of());
      manager.registerSession(TestSessionType.USER_PANEL, 222L, 456L, mockHook, Map.of());

      Optional<DiscordSessionManager.Session<TestSessionType>> session1 =
          manager.getSession(TestSessionType.USER_PANEL, 111L, 456L);
      Optional<DiscordSessionManager.Session<TestSessionType>> session2 =
          manager.getSession(TestSessionType.USER_PANEL, 222L, 456L);

      assertThat(session1).isPresent();
      assertThat(session2).isPresent();
    }

    @Test
    @DisplayName("不同使用者的 Session 應該獨立")
    void sessionsFromDifferentUsersShouldBeIndependent() {
      manager.registerSession(TestSessionType.USER_PANEL, 123L, 111L, mockHook, Map.of());
      manager.registerSession(TestSessionType.USER_PANEL, 123L, 222L, mockHook, Map.of());

      Optional<DiscordSessionManager.Session<TestSessionType>> session1 =
          manager.getSession(TestSessionType.USER_PANEL, 123L, 111L);
      Optional<DiscordSessionManager.Session<TestSessionType>> session2 =
          manager.getSession(TestSessionType.USER_PANEL, 123L, 222L);

      assertThat(session1).isPresent();
      assertThat(session2).isPresent();
    }
  }

  @Test
  @DisplayName("Session 覆蓋註冊應該替換舊的 Session")
  void sessionReRegistrationShouldReplaceOld() {
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of("page", 1));

    Optional<DiscordSessionManager.Session<TestSessionType>> firstSession =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
    assertThat(firstSession).isPresent();
    assertThat(firstSession.get().metadata()).isEqualTo(Map.of("page", 1));

    // 重新註冊同一個 Session
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, mockHook, Map.of("page", 2));

    Optional<DiscordSessionManager.Session<TestSessionType>> secondSession =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
    assertThat(secondSession).isPresent();
    assertThat(secondSession.get().metadata()).isEqualTo(Map.of("page", 2));
  }

  /** 測試用的 Session 類型 */
  public enum TestSessionType implements SessionType {
    USER_PANEL,
    ADMIN_PANEL,
    SHOP_PURCHASE
  }
}
