package ltdjms.discord.redemption.services;

import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.redemption.domain.ProductRedemptionTransaction;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedemptionService.
 */
@ExtendWith(MockitoExtension.class)
class RedemptionServiceTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_PRODUCT_ID = 1L;
    private static final long TEST_USER_ID = 987654321098765432L;

    @Mock
    private RedemptionCodeRepository codeRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RedemptionCodeGenerator codeGenerator;
    @Mock
    private BalanceAdjustmentService balanceAdjustmentService;
    @Mock
    private GameTokenService gameTokenService;
    @Mock
    private CurrencyTransactionService currencyTransactionService;
    @Mock
    private GameTokenTransactionService gameTokenTransactionService;
    @Mock
    private ProductRedemptionTransactionService productRedemptionTransactionService;
    @Mock
    private DomainEventPublisher eventPublisher;

    private RedemptionService redemptionService;

    @BeforeEach
    void setUp() {
        redemptionService = new RedemptionService(
                codeRepository, productRepository, codeGenerator,
                balanceAdjustmentService, gameTokenService,
                currencyTransactionService, gameTokenTransactionService,
                productRedemptionTransactionService, eventPublisher);
    }

    @Nested
    @DisplayName("generateCodes")
    class GenerateCodesTests {

        @Test
        @DisplayName("should generate codes successfully")
        void shouldGenerateCodesSuccessfully() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(TEST_PRODUCT_ID, TEST_GUILD_ID, "Test", null,
                    null, null, now, now);

            when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
            when(codeGenerator.generate())
                    .thenReturn("ABCD1234EFGH5678")
                    .thenReturn("WXYZ9876MNPQ3456");
            when(codeRepository.existsByCode(any())).thenReturn(false);
            when(codeRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<RedemptionCode> codes = invocation.getArgument(0);
                return codes.stream()
                        .map(c -> new RedemptionCode(1L, c.code(), c.productId(), c.guildId(),
                                c.expiresAt(), null, null, c.createdAt(), c.invalidatedAt(), c.quantity()))
                        .toList();
            });

            // When
            Result<List<RedemptionCode>, DomainError> result =
                    redemptionService.generateCodes(TEST_PRODUCT_ID, 2, null);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("should reject zero count")
        void shouldRejectZeroCount() {
            // When
            Result<List<RedemptionCode>, DomainError> result =
                    redemptionService.generateCodes(TEST_PRODUCT_ID, 0, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("大於 0");
        }

        @Test
        @DisplayName("should reject count exceeding maximum")
        void shouldRejectCountExceedingMaximum() {
            // When
            Result<List<RedemptionCode>, DomainError> result =
                    redemptionService.generateCodes(TEST_PRODUCT_ID, 101, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("100");
        }

        @Test
        @DisplayName("should reject for non-existent product")
        void shouldRejectForNonExistentProduct() {
            // Given
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Result<List<RedemptionCode>, DomainError> result =
                    redemptionService.generateCodes(999L, 5, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("找不到");
        }
    }

    @Nested
    @DisplayName("redeemCode - successful cases")
    class RedeemCodeSuccessTests {

        @Test
        @DisplayName("should redeem code without reward")
        void shouldRedeemCodeWithoutReward() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(TEST_PRODUCT_ID, TEST_GUILD_ID, "VIP 服務", "專人服務",
                    null, null, now, now);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    TEST_GUILD_ID, null, null, null, now, null, 1);
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID, "VIP 服務",
                    "ABCD1234EFGH5678", 1, null, null);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
            when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
            when(codeRepository.update(any())).thenAnswer(i -> i.getArgument(0));
            when(productRedemptionTransactionService.recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any()))
                    .thenReturn(transaction);

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().product().name()).isEqualTo("VIP 服務");
            assertThat(result.getValue().rewardedAmount()).isNull();
            verify(codeRepository).update(any());
            verify(productRedemptionTransactionService).recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any());
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should redeem code with currency reward")
        void shouldRedeemCodeWithCurrencyReward() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(TEST_PRODUCT_ID, TEST_GUILD_ID, "禮包", null,
                    Product.RewardType.CURRENCY, 1000L, now, now);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    TEST_GUILD_ID, null, null, null, now, null, 1);
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID, "禮包",
                    "ABCD1234EFGH5678", 1,
                    ProductRedemptionTransaction.RewardType.CURRENCY, 1000L);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
            when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
            when(codeRepository.update(any())).thenAnswer(i -> i.getArgument(0));
            when(balanceAdjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L))
                    .thenReturn(Result.ok(new BalanceAdjustmentService.BalanceAdjustmentResult(
                            TEST_GUILD_ID, TEST_USER_ID, 0L, 1000L, 1000L, "Coins", "🪙")));
            when(productRedemptionTransactionService.recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any()))
                    .thenReturn(transaction);

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().rewardedAmount()).isEqualTo(1000L);
            verify(balanceAdjustmentService).tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L);
            verify(productRedemptionTransactionService).recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any());
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should redeem code with token reward")
        void shouldRedeemCodeWithTokenReward() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(TEST_PRODUCT_ID, TEST_GUILD_ID, "代幣包", null,
                    Product.RewardType.TOKEN, 50L, now, now);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    TEST_GUILD_ID, null, null, null, now, null, 1);
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID, "代幣包",
                    "ABCD1234EFGH5678", 1,
                    ProductRedemptionTransaction.RewardType.TOKEN, 50L);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
            when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
            when(codeRepository.update(any())).thenAnswer(i -> i.getArgument(0));
            when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L))
                    .thenReturn(Result.ok(new GameTokenService.TokenAdjustmentResult(
                            TEST_GUILD_ID, TEST_USER_ID, 0L, 50L, 50L)));
            when(productRedemptionTransactionService.recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any()))
                    .thenReturn(transaction);

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().rewardedAmount()).isEqualTo(50L);
            verify(gameTokenService).tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L);
            verify(productRedemptionTransactionService).recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any());
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should convert code to uppercase")
        void shouldConvertCodeToUppercase() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(TEST_PRODUCT_ID, TEST_GUILD_ID, "Test", null,
                    null, null, now, now);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    TEST_GUILD_ID, null, null, null, now, null, 1);
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID, "Test",
                    "ABCD1234EFGH5678", 1, null, null);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
            when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
            when(codeRepository.update(any())).thenAnswer(i -> i.getArgument(0));
            when(productRedemptionTransactionService.recordTransaction(
                    eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any()))
                    .thenReturn(transaction);

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("abcd1234efgh5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isOk()).isTrue();
        }
    }

    @Nested
    @DisplayName("redeemCode - failure cases")
    class RedeemCodeFailureTests {

        @Test
        @DisplayName("should reject blank code")
        void shouldRejectBlankCode() {
            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("   ", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("無效");
        }

        @Test
        @DisplayName("should reject non-existent code")
        void shouldRejectNonExistentCode() {
            // Given
            when(codeRepository.findByCode("NONEXISTENT12345")).thenReturn(Optional.empty());

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("NONEXISTENT12345", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("無效");
        }

        @Test
        @DisplayName("should reject code from different guild")
        void shouldRejectCodeFromDifferentGuild() {
            // Given
            Instant now = Instant.now();
            long otherGuildId = 999999999999999999L;
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    otherGuildId, null, null, null, now, null, 1);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            // Should not reveal the code belongs to another guild
            assertThat(result.getError().message()).contains("無效");
        }

        @Test
        @DisplayName("should reject already redeemed code")
        void shouldRejectAlreadyRedeemedCode() {
            // Given
            Instant now = Instant.now();
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    TEST_GUILD_ID, null, 999L, now, now, null, 1);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("已被使用");
        }

        @Test
        @DisplayName("should reject expired code")
        void shouldRejectExpiredCode() {
            // Given
            Instant now = Instant.now();
            Instant pastDate = now.minus(1, ChronoUnit.DAYS);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", TEST_PRODUCT_ID,
                    TEST_GUILD_ID, pastDate, null, null, now, null, 1);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("已過期");
        }

        @Test
        @DisplayName("should reject invalidated code")
        void shouldRejectInvalidatedCode() {
            // Given
            Instant now = Instant.now();
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", null,
                    TEST_GUILD_ID, null, null, null, now, now, 1);

            when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));

            // When
            Result<RedemptionService.RedemptionResult, DomainError> result =
                    redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("已失效");
        }
    }

    @Nested
    @DisplayName("RedemptionResult formatting")
    class RedemptionResultFormattingTests {

        @Test
        @DisplayName("should format success message without reward")
        void shouldFormatSuccessMessageWithoutReward() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(1L, TEST_GUILD_ID, "VIP 服務", "專人服務",
                    null, null, now, now);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", 1L,
                    TEST_GUILD_ID, null, TEST_USER_ID, now, now, null, 1);

            RedemptionService.RedemptionResult result =
                    new RedemptionService.RedemptionResult(code, product, null);

            // When
            String message = result.formatSuccessMessage();

            // Then
            assertThat(message).contains("VIP 服務");
            assertThat(message).contains("專人服務");
            assertThat(message).doesNotContain("獎勵");
        }

        @Test
        @DisplayName("should format success message with currency reward")
        void shouldFormatSuccessMessageWithCurrencyReward() {
            // Given
            Instant now = Instant.now();
            Product product = new Product(1L, TEST_GUILD_ID, "禮包", null,
                    Product.RewardType.CURRENCY, 1000L, now, now);
            RedemptionCode code = new RedemptionCode(1L, "ABCD1234EFGH5678", 1L,
                    TEST_GUILD_ID, null, TEST_USER_ID, now, now, null, 1);

            RedemptionService.RedemptionResult result =
                    new RedemptionService.RedemptionResult(code, product, 1000L);

            // When
            String message = result.formatSuccessMessage();

            // Then
            assertThat(message).contains("禮包");
            assertThat(message).contains("獎勵");
            assertThat(message).contains("1,000 貨幣");
        }
    }
}
