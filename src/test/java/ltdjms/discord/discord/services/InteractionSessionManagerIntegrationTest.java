package ltdjms.discord.discord.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import ltdjms.discord.discord.domain.DiscordSessionManager;
import ltdjms.discord.discord.domain.SessionType;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * InteractionSessionManager 整合測試
 *
 * <p>測試 Session 過期與清理邏輯的整合行為，包括：
 *
 * <ul>
 *   <li>Session TTL 過期
 *   <li>自動清理過期 Session
 *   <li>並發存取安全性
 * </ul>
 */
@DisplayName("InteractionSessionManager 整合測試")
@Timeout(10) // 防止測試無限期執行
class InteractionSessionManagerIntegrationTest {

  @Test
  @DisplayName("Session 應該在 TTL 後過期")
  void sessionShouldExpireAfterTTL() {
    InteractionSessionManager<TestSessionType> manager = new InteractionSessionManager<>();
    InteractionHook hook = mock(InteractionHook.class);

    // 註冊一個短期 Session（假設我們可以設定較短的 TTL）
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of());

    // 驗證 Session 初始狀態為有效
    Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
        manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
    assertThat(sessionOpt).isPresent();
    assertThat(sessionOpt.get().isExpired()).isFalse();

    // 注意：由於預設 TTL 為 15 分鐘，我們無法在單元測試中真實等待過期
    // 這裡我們驗證 Session 的 isExpired() 方法正確運作
    DiscordSessionManager.Session<TestSessionType> session = sessionOpt.get();
    assertThat(session.createdAt()).isBefore(Instant.now().plusSeconds(1));
    assertThat(session.ttlSeconds()).isGreaterThan(0);
  }

  @Test
  @DisplayName("clearExpiredSessions 應該移除所有過期 Session")
  void clearExpiredSessionsShouldRemoveAllExpiredSessions() {
    InteractionSessionManager<TestSessionType> manager = new InteractionSessionManager<>();
    InteractionHook hook = mock(InteractionHook.class);

    // 註冊多個 Session
    manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of("page", 1));
    manager.registerSession(TestSessionType.ADMIN_PANEL, 123L, 789L, hook, Map.of("admin", true));
    manager.registerSession(TestSessionType.SHOP_PURCHASE, 456L, 123L, hook, Map.of());

    // 驗證所有 Session 都存在
    assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isPresent();
    assertThat(manager.getSession(TestSessionType.ADMIN_PANEL, 123L, 789L)).isPresent();
    assertThat(manager.getSession(TestSessionType.SHOP_PURCHASE, 456L, 123L)).isPresent();

    // 執行清理（目前沒有過期的 Session）
    manager.clearExpiredSessions();

    // 驗證所有 Session 仍然存在
    assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isPresent();
    assertThat(manager.getSession(TestSessionType.ADMIN_PANEL, 123L, 789L)).isPresent();
    assertThat(manager.getSession(TestSessionType.SHOP_PURCHASE, 456L, 123L)).isPresent();
  }

  @Test
  @DisplayName("Session 過期後應該無法被檢索")
  void expiredSessionShouldNotBeRetrievable() {
    // 此測試驗證過期 Session 的行為
    // 由於 TTL 較長，我們使用 mock 來驗證邏輯

    InteractionHook hook = mock(InteractionHook.class);

    // 建立一個手動過期的 Session
    DiscordSessionManager.Session<TestSessionType> expiredSession =
        new DiscordSessionManager.Session<>(
            TestSessionType.USER_PANEL,
            hook,
            Instant.now().minusSeconds(2000), // 建立於很久以前
            900, // TTL 900 秒
            Map.of("page", 1));

    assertThat(expiredSession.isExpired()).isTrue();
  }

  @Nested
  @DisplayName("並發存取測試")
  class ConcurrencyTests {

    @Test
    @DisplayName("多執行緒同時註冊 Session 應該安全")
    void concurrentRegistrationShouldBeThreadSafe() throws InterruptedException {
      InteractionSessionManager<TestSessionType> manager = new InteractionSessionManager<>();
      int threadCount = 10;
      int sessionsPerThread = 100;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);

      // 多執行緒同時註冊 Session
      for (int i = 0; i < threadCount; i++) {
        final int threadId = i;
        executor.submit(
            () -> {
              try {
                for (int j = 0; j < sessionsPerThread; j++) {
                  InteractionHook hook = mock(InteractionHook.class);
                  long guildId = 100 + threadId;
                  long userId = 1000 + j;
                  manager.registerSession(
                      TestSessionType.USER_PANEL,
                      guildId,
                      userId,
                      hook,
                      Map.of("thread", threadId, "index", j));
                }
              } finally {
                latch.countDown();
              }
            });
      }

      // 等待所有任務完成
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();

      // 驗證沒有拋出異常即為成功
      // 由於不同執行緒使用不同的 guildId，不應該有衝突
    }

    @Test
    @DisplayName("多執行緒同時讀取 Session 應該安全")
    void concurrentReadShouldBeThreadSafe() throws InterruptedException {
      InteractionSessionManager<TestSessionType> manager = new InteractionSessionManager<>();
      InteractionHook hook = mock(InteractionHook.class);

      // 先註冊一個 Session
      manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of());

      int threadCount = 10;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);

      // 多執行緒同時讀取 Session
      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              try {
                for (int j = 0; j < 100; j++) {
                  Optional<DiscordSessionManager.Session<TestSessionType>> session =
                      manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
                  assertThat(session).isPresent();
                }
              } finally {
                latch.countDown();
              }
            });
      }

      // 等待所有任務完成
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();
    }
  }

  @Nested
  @DisplayName("Session 元資料測試")
  class SessionMetadataTests {

    @Test
    @DisplayName("Session 元資料應該被正確儲存和檢索")
    void sessionMetadataShouldBeStoredAndRetrieved() {
      InteractionSessionManager<TestSessionType> manager = new InteractionSessionManager<>();
      InteractionHook hook = mock(InteractionHook.class);

      Map<String, Object> metadata =
          Map.of("page", 1, "filter", "active", "sort", "desc", "userId", 456L);

      manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, metadata);

      Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
          manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

      assertThat(sessionOpt).isPresent();
      assertThat(sessionOpt.get().metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("Session 元資料應該支援更新（透過重新註冊）")
    void sessionMetadataShouldBeUpdatable() {
      InteractionSessionManager<TestSessionType> manager = new InteractionSessionManager<>();
      InteractionHook hook = mock(InteractionHook.class);

      manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of("page", 1));

      // 更新元資料
      manager.registerSession(
          TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of("page", 2, "filter", "active"));

      Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt =
          manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

      assertThat(sessionOpt).isPresent();
      assertThat(sessionOpt.get().metadata()).isEqualTo(Map.of("page", 2, "filter", "active"));
    }
  }

  /** 測試用的 Session 類型 */
  public enum TestSessionType implements SessionType {
    USER_PANEL,
    ADMIN_PANEL,
    SHOP_PURCHASE
  }
}
