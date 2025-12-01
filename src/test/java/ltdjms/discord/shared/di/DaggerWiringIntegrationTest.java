package ltdjms.discord.shared.di;

import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.commands.BalanceAdjustmentCommandHandler;
import ltdjms.discord.currency.commands.BalanceCommandHandler;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1ConfigCommandHandler;
import ltdjms.discord.gametoken.commands.GameTokenAdjustCommandHandler;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.EnvironmentConfig;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Dagger DI wiring.
 * Verifies that all dependencies are correctly provided by the AppComponent.
 */
@Testcontainers
class DaggerWiringIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle of the PostgreSQLContainer
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("currency_bot_test")
            .withUsername("test")
            .withPassword("test");

    private static AppComponent appComponent;
    private static Path tempDir;

    @BeforeAll
    static void setUp() throws IOException {
        // Create a temporary directory with a .env file pointing to the test container
        tempDir = Files.createTempDirectory("dagger-test");
        Path dotEnvFile = tempDir.resolve(".env");
        String dotEnvContent = String.format(
                "DISCORD_BOT_TOKEN=test-token%n" +
                "DB_URL=%s%n" +
                "DB_USERNAME=%s%n" +
                "DB_PASSWORD=%s%n",
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        Files.writeString(dotEnvFile, dotEnvContent);

        // Create EnvironmentConfig using the temp directory
        EnvironmentConfig envConfig = new EnvironmentConfig(tempDir);

        // Build Dagger component
        appComponent = DaggerAppComponent.builder()
                .databaseModule(new DatabaseModule(envConfig))
                .build();

        // Apply schema
        applySchema(appComponent.dataSource());
    }

    private static void applySchema(DataSource dataSource) {
        try (var is = DaggerWiringIntegrationTest.class.getClassLoader()
                .getResourceAsStream("db/schema.sql")) {
            if (is == null) {
                throw new RuntimeException("schema.sql not found on classpath");
            }
            String schema = new String(is.readAllBytes());

            try (var conn = dataSource.getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute(schema);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply schema", e);
        }
    }

    @Nested
    @DisplayName("Configuration Dependencies")
    class ConfigurationDependencies {

        @Test
        @DisplayName("should provide EnvironmentConfig")
        void shouldProvideEnvironmentConfig() {
            EnvironmentConfig config = appComponent.environmentConfig();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("should provide DatabaseConfig")
        void shouldProvideDatabaseConfig() {
            DatabaseConfig config = appComponent.databaseConfig();
            assertThat(config).isNotNull();
        }
    }

    @Nested
    @DisplayName("Database Dependencies")
    class DatabaseDependencies {

        @Test
        @DisplayName("should provide DataSource")
        void shouldProvideDataSource() {
            DataSource dataSource = appComponent.dataSource();
            assertThat(dataSource).isNotNull();
        }

        @Test
        @DisplayName("should provide DSLContext")
        void shouldProvideDSLContext() {
            DSLContext dslContext = appComponent.dslContext();
            assertThat(dslContext).isNotNull();
        }
    }

    @Nested
    @DisplayName("Currency Repository Dependencies")
    class CurrencyRepositoryDependencies {

        @Test
        @DisplayName("should provide MemberCurrencyAccountRepository")
        void shouldProvideMemberCurrencyAccountRepository() {
            MemberCurrencyAccountRepository repo = appComponent.memberCurrencyAccountRepository();
            assertThat(repo).isNotNull();
        }

        @Test
        @DisplayName("should provide GuildCurrencyConfigRepository")
        void shouldProvideGuildCurrencyConfigRepository() {
            GuildCurrencyConfigRepository repo = appComponent.guildCurrencyConfigRepository();
            assertThat(repo).isNotNull();
        }
    }

    @Nested
    @DisplayName("Game Token Repository Dependencies")
    class GameTokenRepositoryDependencies {

        @Test
        @DisplayName("should provide GameTokenAccountRepository")
        void shouldProvideGameTokenAccountRepository() {
            GameTokenAccountRepository repo = appComponent.gameTokenAccountRepository();
            assertThat(repo).isNotNull();
        }

        @Test
        @DisplayName("should provide DiceGame1ConfigRepository")
        void shouldProvideDiceGame1ConfigRepository() {
            DiceGame1ConfigRepository repo = appComponent.diceGame1ConfigRepository();
            assertThat(repo).isNotNull();
        }
    }

    @Nested
    @DisplayName("Currency Service Dependencies")
    class CurrencyServiceDependencies {

        @Test
        @DisplayName("should provide BalanceService")
        void shouldProvideBalanceService() {
            BalanceService service = appComponent.balanceService();
            assertThat(service).isNotNull();
        }

        @Test
        @DisplayName("should provide CurrencyConfigService")
        void shouldProvideCurrencyConfigService() {
            CurrencyConfigService service = appComponent.currencyConfigService();
            assertThat(service).isNotNull();
        }

        @Test
        @DisplayName("should provide BalanceAdjustmentService")
        void shouldProvideBalanceAdjustmentService() {
            BalanceAdjustmentService service = appComponent.balanceAdjustmentService();
            assertThat(service).isNotNull();
        }
    }

    @Nested
    @DisplayName("Game Token Service Dependencies")
    class GameTokenServiceDependencies {

        @Test
        @DisplayName("should provide GameTokenService")
        void shouldProvideGameTokenService() {
            GameTokenService service = appComponent.gameTokenService();
            assertThat(service).isNotNull();
        }

        @Test
        @DisplayName("should provide DiceGame1Service")
        void shouldProvideDiceGame1Service() {
            DiceGame1Service service = appComponent.diceGame1Service();
            assertThat(service).isNotNull();
        }
    }

    @Nested
    @DisplayName("Command Handler Dependencies")
    class CommandHandlerDependencies {

        @Test
        @DisplayName("should provide BalanceCommandHandler")
        void shouldProvideBalanceCommandHandler() {
            BalanceCommandHandler handler = appComponent.balanceCommandHandler();
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("should provide CurrencyConfigCommandHandler")
        void shouldProvideCurrencyConfigCommandHandler() {
            CurrencyConfigCommandHandler handler = appComponent.currencyConfigCommandHandler();
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("should provide BalanceAdjustmentCommandHandler")
        void shouldProvideBalanceAdjustmentCommandHandler() {
            BalanceAdjustmentCommandHandler handler = appComponent.balanceAdjustmentCommandHandler();
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("should provide GameTokenAdjustCommandHandler")
        void shouldProvideGameTokenAdjustCommandHandler() {
            GameTokenAdjustCommandHandler handler = appComponent.gameTokenAdjustCommandHandler();
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("should provide DiceGame1CommandHandler")
        void shouldProvideDiceGame1CommandHandler() {
            DiceGame1CommandHandler handler = appComponent.diceGame1CommandHandler();
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("should provide DiceGame1ConfigCommandHandler")
        void shouldProvideDiceGame1ConfigCommandHandler() {
            DiceGame1ConfigCommandHandler handler = appComponent.diceGame1ConfigCommandHandler();
            assertThat(handler).isNotNull();
        }
    }

    @Nested
    @DisplayName("SlashCommandListener Dependency")
    class SlashCommandListenerDependency {

        @Test
        @DisplayName("should provide SlashCommandListener")
        void shouldProvideSlashCommandListener() {
            SlashCommandListener listener = appComponent.slashCommandListener();
            assertThat(listener).isNotNull();
        }

        @Test
        @DisplayName("SlashCommandListener should be singleton")
        void shouldBeSingleton() {
            SlashCommandListener listener1 = appComponent.slashCommandListener();
            SlashCommandListener listener2 = appComponent.slashCommandListener();
            assertThat(listener1).isSameAs(listener2);
        }
    }

    @Nested
    @DisplayName("Singleton Behavior")
    class SingletonBehavior {

        @Test
        @DisplayName("DataSource should be singleton")
        void dataSourceShouldBeSingleton() {
            DataSource ds1 = appComponent.dataSource();
            DataSource ds2 = appComponent.dataSource();
            assertThat(ds1).isSameAs(ds2);
        }

        @Test
        @DisplayName("DSLContext should be singleton")
        void dslContextShouldBeSingleton() {
            DSLContext ctx1 = appComponent.dslContext();
            DSLContext ctx2 = appComponent.dslContext();
            assertThat(ctx1).isSameAs(ctx2);
        }

        @Test
        @DisplayName("Repositories should be singletons")
        void repositoriesShouldBeSingletons() {
            assertThat(appComponent.memberCurrencyAccountRepository())
                    .isSameAs(appComponent.memberCurrencyAccountRepository());
            assertThat(appComponent.guildCurrencyConfigRepository())
                    .isSameAs(appComponent.guildCurrencyConfigRepository());
            assertThat(appComponent.gameTokenAccountRepository())
                    .isSameAs(appComponent.gameTokenAccountRepository());
            assertThat(appComponent.diceGame1ConfigRepository())
                    .isSameAs(appComponent.diceGame1ConfigRepository());
        }

        @Test
        @DisplayName("Services should be singletons")
        void servicesShouldBeSingletons() {
            assertThat(appComponent.balanceService())
                    .isSameAs(appComponent.balanceService());
            assertThat(appComponent.currencyConfigService())
                    .isSameAs(appComponent.currencyConfigService());
            assertThat(appComponent.balanceAdjustmentService())
                    .isSameAs(appComponent.balanceAdjustmentService());
            assertThat(appComponent.gameTokenService())
                    .isSameAs(appComponent.gameTokenService());
            assertThat(appComponent.diceGame1Service())
                    .isSameAs(appComponent.diceGame1Service());
        }
    }
}
