package ltdjms.discord.discord.domain;

import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * DiscordSessionManager 介面契約測試
 *
 * <p>此測試定義 DiscordSessionManager 介面的行為契約。
 * 所有實作都必須符合此契約。
 */
@DisplayName("DiscordSessionManager 介面契約測試")
class DiscordSessionManagerTest {

    /**
     * 測試用的 Session 類型枚舉
     */
    public enum TestSessionType implements SessionType {
        USER_PANEL,
        ADMIN_PANEL,
        SHOP_PURCHASE
    }

    /**
     * 測試用的抽象 SessionManager
     */
    private static class TestDiscordSessionManager implements DiscordSessionManager<TestSessionType> {
        private final Map<String, DiscordSessionManager.Session<TestSessionType>> sessions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void registerSession(TestSessionType type, long guildId, long userId,
                                   InteractionHook hook, Map<String, Object> metadata) {
            String key = getKey(type, guildId, userId);
            DiscordSessionManager.Session<TestSessionType> session = new DiscordSessionManager.Session<>(type, hook, Instant.now(), 900, metadata);
            sessions.put(key, session);
        }

        @Override
        public Optional<DiscordSessionManager.Session<TestSessionType>> getSession(TestSessionType type, long guildId, long userId) {
            String key = getKey(type, guildId, userId);
            DiscordSessionManager.Session<TestSessionType> session = sessions.get(key);
            if (session == null || session.isExpired()) {
                return Optional.empty();
            }
            return Optional.of(session);
        }

        @Override
        public void clearSession(TestSessionType type, long guildId, long userId) {
            String key = getKey(type, guildId, userId);
            sessions.remove(key);
        }

        @Override
        public void clearExpiredSessions() {
            sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }

        private String getKey(TestSessionType type, long guildId, long userId) {
            return type.name() + ":" + guildId + ":" + userId;
        }

        // 測試用方法
        int getSessionCount() {
            return sessions.size();
        }
    }

    @Test
    @DisplayName("registerSession 應該註冊新的 Session")
    void registerSessionShouldRegisterNewSession() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook = mock(InteractionHook.class);

        manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of("page", 1));

        assertThat(manager.getSessionCount()).isEqualTo(1);

        Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt = manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().type()).isEqualTo(TestSessionType.USER_PANEL);
    }

    @Test
    @DisplayName("registerSession 應該包含元資料")
    void registerSessionShouldIncludeMetadata() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook = mock(InteractionHook.class);
        Map<String, Object> metadata = Map.of("page", 1, "filter", "active");

        manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, metadata);

        Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt = manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("getSession 應該返回已註冊的 Session")
    void getSessionShouldReturnRegisteredSession() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook = mock(InteractionHook.class);

        manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of());

        Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt = manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().hook()).isEqualTo(hook);
    }

    @Test
    @DisplayName("getSession 對於不存在的 Session 應該返回空")
    void getSessionShouldReturnEmptyForNonExistent() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();

        Optional<DiscordSessionManager.Session<TestSessionType>> sessionOpt = manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);

        assertThat(sessionOpt).isEmpty();
    }

    @Test
    @DisplayName("getSession 對於不同類型的 Session 應該分開處理")
    void getSessionShouldHandleDifferentTypesSeparately() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook1 = mock(InteractionHook.class);
        InteractionHook hook2 = mock(InteractionHook.class);

        manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook1, Map.of());
        manager.registerSession(TestSessionType.ADMIN_PANEL, 123L, 456L, hook2, Map.of());

        Optional<DiscordSessionManager.Session<TestSessionType>> userSession = manager.getSession(TestSessionType.USER_PANEL, 123L, 456L);
        Optional<DiscordSessionManager.Session<TestSessionType>> adminSession = manager.getSession(TestSessionType.ADMIN_PANEL, 123L, 456L);

        assertThat(userSession).isPresent();
        assertThat(adminSession).isPresent();
        assertThat(userSession.get().hook()).isNotEqualTo(adminSession.get().hook());
    }

    @Test
    @DisplayName("clearSession 應該移除指定的 Session")
    void clearSessionShouldRemoveSession() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook = mock(InteractionHook.class);

        manager.registerSession(TestSessionType.USER_PANEL, 123L, 456L, hook, Map.of());
        assertThat(manager.getSessionCount()).isEqualTo(1);

        manager.clearSession(TestSessionType.USER_PANEL, 123L, 456L);

        assertThat(manager.getSessionCount()).isEqualTo(0);
        assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isEmpty();
    }

    @Test
    @DisplayName("clearExpiredSessions 應該移除過期的 Session")
    void clearExpiredSessionsShouldRemoveExpired() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook = mock(InteractionHook.class);

        // 手動建立一個已過期的 Session（使用較短的 TTL）
        DiscordSessionManager.Session<TestSessionType> expiredSession = new DiscordSessionManager.Session<>(
            TestSessionType.USER_PANEL,
            hook,
            Instant.now().minusSeconds(1000), // 建立於 1000 秒前
            900, // TTL 900 秒
            Map.of()
        );

        // 直接注入過期的 Session
        manager.sessions.put("USER_PANEL:123:456", expiredSession);

        assertThat(manager.getSessionCount()).isEqualTo(1);
        assertThat(manager.getSession(TestSessionType.USER_PANEL, 123L, 456L)).isEmpty();

        manager.clearExpiredSessions();

        assertThat(manager.getSessionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Session.isExpired 應該正確判斷過期狀態")
    void sessionIsExpiredShouldCorrectlyDetermineExpiration() {
        InteractionHook hook = mock(InteractionHook.class);

        // 未過期的 Session
        DiscordSessionManager.Session<TestSessionType> validSession = new DiscordSessionManager.Session<>(
            TestSessionType.USER_PANEL,
            hook,
            Instant.now(),
            900,
            Map.of()
        );

        // 已過期的 Session
        DiscordSessionManager.Session<TestSessionType> expiredSession = new DiscordSessionManager.Session<>(
            TestSessionType.USER_PANEL,
            hook,
            Instant.now().minusSeconds(1000),
            900,
            Map.of()
        );

        assertThat(validSession.isExpired()).isFalse();
        assertThat(expiredSession.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Session 記錄應該包含所有必要資訊")
    void sessionRecordShouldContainAllRequiredInfo() {
        InteractionHook hook = mock(InteractionHook.class);
        Map<String, Object> metadata = Map.of("page", 1, "filter", "active");

        DiscordSessionManager.Session<TestSessionType> session = new DiscordSessionManager.Session<>(
            TestSessionType.USER_PANEL,
            hook,
            Instant.now(),
            900,
            metadata
        );

        assertThat(session.type()).isEqualTo(TestSessionType.USER_PANEL);
        assertThat(session.hook()).isEqualTo(hook);
        assertThat(session.ttlSeconds()).isEqualTo(900);
        assertThat(session.metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("多個 Guild 的 Session 應該獨立管理")
    void sessionsFromMultipleGuildsShouldBeIndependent() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook1 = mock(InteractionHook.class);
        InteractionHook hook2 = mock(InteractionHook.class);

        manager.registerSession(TestSessionType.USER_PANEL, 111L, 456L, hook1, Map.of());
        manager.registerSession(TestSessionType.USER_PANEL, 222L, 456L, hook2, Map.of());

        assertThat(manager.getSessionCount()).isEqualTo(2);

        Optional<DiscordSessionManager.Session<TestSessionType>> session1 = manager.getSession(TestSessionType.USER_PANEL, 111L, 456L);
        Optional<DiscordSessionManager.Session<TestSessionType>> session2 = manager.getSession(TestSessionType.USER_PANEL, 222L, 456L);

        assertThat(session1).isPresent();
        assertThat(session2).isPresent();
        assertThat(session1.get().hook()).isNotEqualTo(session2.get().hook());
    }

    @Test
    @DisplayName("多個使用者的 Session 應該獨立管理")
    void sessionsFromMultipleUsersShouldBeIndependent() {
        TestDiscordSessionManager manager = new TestDiscordSessionManager();
        InteractionHook hook1 = mock(InteractionHook.class);
        InteractionHook hook2 = mock(InteractionHook.class);

        manager.registerSession(TestSessionType.USER_PANEL, 123L, 111L, hook1, Map.of());
        manager.registerSession(TestSessionType.USER_PANEL, 123L, 222L, hook2, Map.of());

        assertThat(manager.getSessionCount()).isEqualTo(2);

        Optional<DiscordSessionManager.Session<TestSessionType>> session1 = manager.getSession(TestSessionType.USER_PANEL, 123L, 111L);
        Optional<DiscordSessionManager.Session<TestSessionType>> session2 = manager.getSession(TestSessionType.USER_PANEL, 123L, 222L);

        assertThat(session1).isPresent();
        assertThat(session2).isPresent();
        assertThat(session1.get().hook()).isNotEqualTo(session2.get().hook());
    }

    @Test
    @DisplayName("介面方法簽章應該正確")
    void interfaceMethodSignaturesShouldBeCorrect() {
        assertThat(DiscordSessionManager.class)
            .hasDeclaredMethods(
                "registerSession",
                "getSession",
                "clearSession",
                "clearExpiredSessions"
            );
    }
}
