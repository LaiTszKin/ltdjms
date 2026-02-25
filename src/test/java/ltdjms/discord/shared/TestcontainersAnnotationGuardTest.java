package ltdjms.discord.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 防止回歸：沒有 Docker 的環境下，Testcontainers 整合測試應自動略過而不是直接失敗。 */
class TestcontainersAnnotationGuardTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ltdjms.discord.shared.cache.CacheInfrastructureIntegrationTest",
        "ltdjms.discord.shared.DatabaseMigrationRunnerIntegrationTest",
        "ltdjms.discord.currency.integration.PostgresIntegrationTestBase",
        "ltdjms.discord.aiagent.integration.services.LangChain4jModifyChannelPermissionsToolIntegrationTest",
        "ltdjms.discord.aiagent.integration.services.ConversationMemoryIntegrationTest",
        "ltdjms.discord.shared.cache.RedisCacheServiceIntegrationTest",
        "ltdjms.discord.aiagent.integration.services.ToolExecutionLogIntegrationTest",
        "ltdjms.discord.aiagent.integration.services.LangChain4jGetChannelPermissionsToolIntegrationTest",
        "ltdjms.discord.shop.integration.EcpayFiatPaymentE2EIntegrationTest"
      })
  void integrationTestShouldEnableDisabledWithoutDockerFlag(String className)
      throws ClassNotFoundException {
    Class<?> testClass = Class.forName(className);
    Testcontainers annotation = testClass.getAnnotation(Testcontainers.class);

    assertThat(annotation).as("Class %s should have @Testcontainers", className).isNotNull();
    assertThat(annotation.disabledWithoutDocker())
        .as("Class %s should skip when Docker is unavailable", className)
        .isTrue();
  }
}
