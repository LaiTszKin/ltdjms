package ltdjms.discord.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Caddy ingress 組態測試")
class CaddyIngressConfigTest {

  @Test
  @DisplayName("Compose 應改用 Caddy 並持久化 TLS 狀態")
  void composeShouldUseCaddyIngressWithPersistentTlsState() throws IOException {
    String compose = Files.readString(Path.of("docker-compose.yml"));

    assertThat(compose).contains("\n  caddy:\n");
    assertThat(compose).contains("image: caddy:2-alpine");
    assertThat(compose).contains("network_mode: \"service:bot\"");
    assertThat(compose)
        .contains(
            "APP_PUBLIC_DOMAIN: ${APP_PUBLIC_DOMAIN:?APP_PUBLIC_DOMAIN is required for Caddy HTTPS"
                + " ingress}");
    assertThat(compose)
        .contains(
            "CADDY_ACME_EMAIL: ${CADDY_ACME_EMAIL:?CADDY_ACME_EMAIL is required for Caddy HTTPS"
                + " ingress}");
    assertThat(compose).contains("- \"80:80\"");
    assertThat(compose).contains("- \"443:443\"");
    assertThat(compose).contains("- ./docker/caddy/Caddyfile:/etc/caddy/Caddyfile:ro");
    assertThat(compose).contains("- caddy_data:/data");
    assertThat(compose).contains("- caddy_config:/config");
    assertThat(compose).contains("\n  caddy_data:\n");
    assertThat(compose).contains("\n  caddy_config:\n");
    assertThat(compose).doesNotContain("\n  nginx:\n");
  }

  @Test
  @DisplayName("Caddyfile 應使用公開網域與 loopback proxy")
  void caddyfileShouldUseManagedDomainAndLoopbackProxy() throws IOException {
    String caddyfile = Files.readString(Path.of("docker/caddy/Caddyfile"));

    assertThat(caddyfile).contains("{$APP_PUBLIC_DOMAIN} {");
    assertThat(caddyfile).contains("tls {$CADDY_ACME_EMAIL}");
    assertThat(caddyfile).contains("reverse_proxy 127.0.0.1:8085");
  }

  @Test
  @DisplayName("環境範本與主文件應說明 Caddy HTTPS 前提")
  void envTemplateAndDocsShouldDescribeCaddyIngressPrerequisites() throws IOException {
    String envExample = Files.readString(Path.of(".env.example"));
    String readme = Files.readString(Path.of("README.md"));
    String configurationDoc = Files.readString(Path.of("docs/configuration.md"));
    String gettingStartedDoc = Files.readString(Path.of("docs/getting-started.md"));

    assertThat(envExample).contains("APP_PUBLIC_DOMAIN=");
    assertThat(envExample).contains("CADDY_ACME_EMAIL=");
    assertThat(envExample).contains("repo 內 Caddy");

    assertThat(readme).contains("`Caddy` ingress");
    assertThat(readme).contains("`APP_PUBLIC_DOMAIN`");
    assertThat(readme).contains("`CADDY_ACME_EMAIL`");
    assertThat(readme).contains("`docker compose logs caddy`");

    assertThat(configurationDoc).contains("`APP_PUBLIC_DOMAIN`");
    assertThat(configurationDoc).contains("`CADDY_ACME_EMAIL`");
    assertThat(configurationDoc).contains("repo 內管理的 `Caddy` ingress");

    assertThat(gettingStartedDoc).contains("`caddy`：repo 內管理的公開 HTTPS ingress");
    assertThat(gettingStartedDoc).contains("`APP_PUBLIC_DOMAIN`");
    assertThat(gettingStartedDoc).contains("`CADDY_ACME_EMAIL`");
  }
}
