package ltdjms.discord.redemption.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

/** Unit tests for RedemptionService. */
@ExtendWith(MockitoExtension.class)
class RedemptionServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_PRODUCT_ID = 1L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Mock private RedemptionCodeRepository codeRepository;
  @Mock private ProductRepository productRepository;
  @Mock private RedemptionCodeGenerator codeGenerator;
  @Mock private BalanceAdjustmentService balanceAdjustmentService;
  @Mock private GameTokenService gameTokenService;
  @Mock private CurrencyTransactionService currencyTransactionService;
  @Mock private GameTokenTransactionService gameTokenTransactionService;
  @Mock private ProductRedemptionTransactionService productRedemptionTransactionService;
  @Mock private DomainEventPublisher eventPublisher;

  private RedemptionService redemptionService;

  @BeforeEach
  void setUp() {
    redemptionService =
        new RedemptionService(
            codeRepository,
            productRepository,
            codeGenerator,
            balanceAdjustmentService,
            gameTokenService,
            currencyTransactionService,
            gameTokenTransactionService,
            productRedemptionTransactionService,
            eventPublisher);
  }

  @Nested
  @DisplayName("generateCodes")
  class GenerateCodesTests {

    @Test
    @DisplayName("should generate codes successfully")
    void shouldGenerateCodesSuccessfully() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "Test",
              null,
              null,
              null,
              null,
              now,
              now);

      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeGenerator.generate()).thenReturn("ABCD1234EFGH5678").thenReturn("WXYZ9876MNPQ3456");
      when(codeRepository.existsByCode(any())).thenReturn(false);
      when(codeRepository.saveAll(anyList()))
          .thenAnswer(
              invocation -> {
                List<RedemptionCode> codes = invocation.getArgument(0);
                return codes.stream()
                    .map(
                        c ->
                            new RedemptionCode(
                                1L,
                                c.code(),
                                c.productId(),
                                c.guildId(),
                                c.expiresAt(),
                                null,
                                null,
                                c.createdAt(),
                                c.invalidatedAt(),
                                c.quantity()))
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

    @Test
    @DisplayName("should generate codes with quantity")
    void shouldGenerateCodesWithQuantity() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "Test",
              null,
              null,
              null,
              null,
              now,
              now);

      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeGenerator.generate()).thenReturn("ABCD1234EFGH5678").thenReturn("WXYZ9876MNPQ3456");
      when(codeRepository.existsByCode(any())).thenReturn(false);
      when(codeRepository.saveAll(anyList()))
          .thenAnswer(
              invocation -> {
                List<RedemptionCode> codes = invocation.getArgument(0);
                return codes.stream()
                    .map(
                        c ->
                            new RedemptionCode(
                                1L,
                                c.code(),
                                c.productId(),
                                c.guildId(),
                                c.expiresAt(),
                                null,
                                null,
                                c.createdAt(),
                                c.invalidatedAt(),
                                c.quantity()))
                    .toList();
              });

      // When
      Result<List<RedemptionCode>, DomainError> result =
          redemptionService.generateCodes(TEST_PRODUCT_ID, 2, null, 5);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).hasSize(2);
      assertThat(result.getValue().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("should reject zero or negative quantity")
    void shouldRejectZeroOrNegativeQuantity() {
      // When
      Result<List<RedemptionCode>, DomainError> result =
          redemptionService.generateCodes(TEST_PRODUCT_ID, 5, null, 0);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("大於 0");
    }

    @Test
    @DisplayName("should reject quantity exceeding maximum")
    void shouldRejectQuantityExceedingMaximum() {
      // When
      Result<List<RedemptionCode>, DomainError> result =
          redemptionService.generateCodes(TEST_PRODUCT_ID, 5, null, 1001);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("1000");
    }

    @Test
    @DisplayName("should reject expiration time in the past")
    void shouldRejectExpirationTimeInThePast() {
      // When
      Instant expiresAt = Instant.now().minus(1, ChronoUnit.SECONDS);
      Result<List<RedemptionCode>, DomainError> result =
          redemptionService.generateCodes(TEST_PRODUCT_ID, 1, expiresAt);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("過期時間");
    }
  }

  @Nested
  @DisplayName("findByCode")
  class FindByCodeTests {

    @Test
    @DisplayName("should find code by string")
    void shouldFindCodeByString() {
      // Given
      Instant now = Instant.now();
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);
      when(codeRepository.findByCode(anyString())).thenReturn(Optional.of(code));

      // When
      Optional<RedemptionCode> result = redemptionService.findByCode("  ABCD1234eFgH5678  ");

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().code()).isEqualTo("ABCD1234EFGH5678");
    }

    @Test
    @DisplayName("should return empty for null code")
    void shouldReturnEmptyForNullCode() {
      // When
      Optional<RedemptionCode> result = redemptionService.findByCode(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty for blank code")
    void shouldReturnEmptyForBlankCode() {
      // When
      Optional<RedemptionCode> result = redemptionService.findByCode("   ");

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("getCodePage")
  class GetCodePageTests {

    @Test
    @DisplayName("should return code page")
    void shouldReturnCodePage() {
      // Given
      Instant now = Instant.now();
      List<RedemptionCode> codes =
          List.of(
              new RedemptionCode(
                  1L, "CODE1", TEST_PRODUCT_ID, TEST_GUILD_ID, null, null, null, now, null, 1),
              new RedemptionCode(
                  2L, "CODE2", TEST_PRODUCT_ID, TEST_GUILD_ID, null, null, null, now, null, 1));

      when(codeRepository.findByProductId(anyLong(), anyInt(), anyInt())).thenReturn(codes);
      when(codeRepository.countByProductId(TEST_PRODUCT_ID)).thenReturn(15L);

      // When
      RedemptionService.CodePage result = redemptionService.getCodePage(TEST_PRODUCT_ID, 1, 10);

      // Then
      assertThat(result.codes()).hasSize(2);
      assertThat(result.currentPage()).isEqualTo(1);
      assertThat(result.totalPages()).isEqualTo(2);
      assertThat(result.totalCount()).isEqualTo(15);
      assertThat(result.hasNextPage()).isTrue();
      assertThat(result.hasPreviousPage()).isFalse();
    }

    @Test
    @DisplayName("should clamp page number to minimum")
    void shouldClampPageNumberToMinimum() {
      // Given
      when(codeRepository.findByProductId(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
      when(codeRepository.countByProductId(TEST_PRODUCT_ID)).thenReturn(0L);

      // When
      RedemptionService.CodePage result = redemptionService.getCodePage(TEST_PRODUCT_ID, 0, 10);

      // Then
      assertThat(result.currentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("should clamp page size to minimum")
    void shouldClampPageSizeToMinimum() {
      // Given - pageSize < 1 gets clamped to 10 (see implementation)
      when(codeRepository.findByProductId(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
      when(codeRepository.countByProductId(TEST_PRODUCT_ID)).thenReturn(0L);

      // When
      RedemptionService.CodePage result = redemptionService.getCodePage(TEST_PRODUCT_ID, 1, 0);

      // Then - pageSize is clamped to 10 in the implementation
      assertThat(result.pageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("should clamp page number to total pages")
    void shouldClampPageNumberToTotalPages() {
      // Given
      when(codeRepository.findByProductId(TEST_PRODUCT_ID, 10, 10)).thenReturn(List.of());
      when(codeRepository.countByProductId(TEST_PRODUCT_ID)).thenReturn(15L);

      // When
      RedemptionService.CodePage result = redemptionService.getCodePage(TEST_PRODUCT_ID, 5, 10);

      // Then - 15 items with page size 10 = 2 pages
      assertThat(result.currentPage()).isEqualTo(2);
      verify(codeRepository).findByProductId(TEST_PRODUCT_ID, 10, 10);
    }
  }

  @Nested
  @DisplayName("getCodeStats")
  class GetCodeStatsTests {

    @Test
    @DisplayName("should return code stats")
    void shouldReturnCodeStats() {
      // Given
      RedemptionCodeRepository.CodeStats stats =
          new RedemptionCodeRepository.CodeStats(10, 5, 3, 2);
      when(codeRepository.getStatsByProductId(TEST_PRODUCT_ID)).thenReturn(stats);

      // When
      RedemptionCodeRepository.CodeStats result = redemptionService.getCodeStats(TEST_PRODUCT_ID);

      // Then
      assertThat(result.totalCount()).isEqualTo(10);
      assertThat(result.redeemedCount()).isEqualTo(5);
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
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "VIP 服務",
              "專人服務",
              null,
              null,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);
      ProductRedemptionTransaction transaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "VIP 服務",
              "ABCD1234EFGH5678",
              1,
              null,
              null);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeRepository.markAsRedeemedIfAvailable(anyLong(), anyLong(), any())).thenReturn(true);
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
      verify(codeRepository).markAsRedeemedIfAvailable(anyLong(), eq(TEST_USER_ID), any());
      verify(productRedemptionTransactionService)
          .recordTransaction(eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any());
      verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("should redeem code with currency reward")
    void shouldRedeemCodeWithCurrencyReward() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "禮包",
              null,
              Product.RewardType.CURRENCY,
              1000L,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);
      ProductRedemptionTransaction transaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "禮包",
              "ABCD1234EFGH5678",
              1,
              ProductRedemptionTransaction.RewardType.CURRENCY,
              1000L);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeRepository.markAsRedeemedIfAvailable(anyLong(), anyLong(), any())).thenReturn(true);
      when(balanceAdjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L))
          .thenReturn(
              Result.ok(
                  new BalanceAdjustmentService.BalanceAdjustmentResult(
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
      verify(productRedemptionTransactionService)
          .recordTransaction(eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any());
      verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("should redeem short code with currency reward")
    void shouldRedeemShortCodeWithCurrencyReward() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "禮包",
              null,
              Product.RewardType.CURRENCY,
              1000L,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              1L, "ABC", TEST_PRODUCT_ID, TEST_GUILD_ID, null, null, null, now, null, 1);
      ProductRedemptionTransaction transaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "禮包",
              "ABC",
              1,
              ProductRedemptionTransaction.RewardType.CURRENCY,
              1000L);

      when(codeRepository.findByCode("ABC")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeRepository.markAsRedeemedIfAvailable(anyLong(), anyLong(), any())).thenReturn(true);
      when(balanceAdjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L))
          .thenReturn(
              Result.ok(
                  new BalanceAdjustmentService.BalanceAdjustmentResult(
                      TEST_GUILD_ID, TEST_USER_ID, 0L, 1000L, 1000L, "Coins", "🪙")));
      when(productRedemptionTransactionService.recordTransaction(
              eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any()))
          .thenReturn(transaction);

      // When
      Result<RedemptionService.RedemptionResult, DomainError> result =
          redemptionService.redeemCode("ABC", TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardedAmount()).isEqualTo(1000L);
      verify(balanceAdjustmentService).tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L);
    }

    @Test
    @DisplayName("should redeem code with token reward")
    void shouldRedeemCodeWithTokenReward() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "代幣包",
              null,
              Product.RewardType.TOKEN,
              50L,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);
      ProductRedemptionTransaction transaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "代幣包",
              "ABCD1234EFGH5678",
              1,
              ProductRedemptionTransaction.RewardType.TOKEN,
              50L);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeRepository.markAsRedeemedIfAvailable(anyLong(), anyLong(), any())).thenReturn(true);
      when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(
              Result.ok(
                  new GameTokenService.TokenAdjustmentResult(
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
      verify(productRedemptionTransactionService)
          .recordTransaction(eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(product), any());
      verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("should convert code to uppercase")
    void shouldConvertCodeToUppercase() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "Test",
              null,
              null,
              null,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);
      ProductRedemptionTransaction transaction =
          ProductRedemptionTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              TEST_PRODUCT_ID,
              "Test",
              "ABCD1234EFGH5678",
              1,
              null,
              null);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeRepository.markAsRedeemedIfAvailable(anyLong(), anyLong(), any())).thenReturn(true);
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
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              otherGuildId,
              null,
              null,
              null,
              now,
              null,
              1);

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
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              999L,
              now,
              now,
              null,
              1);

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
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              pastDate,
              null,
              null,
              now,
              null,
              1);

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
      RedemptionCode code =
          new RedemptionCode(
              1L, "ABCD1234EFGH5678", null, TEST_GUILD_ID, null, null, null, now, now, 1);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));

      // When
      Result<RedemptionService.RedemptionResult, DomainError> result =
          redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("已失效");
    }

    @Test
    @DisplayName("should reject when code id is null")
    void shouldRejectWhenCodeIdIsNull() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "Test",
              null,
              null,
              null,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              null,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

      // When
      Result<RedemptionService.RedemptionResult, DomainError> result =
          redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("兌換碼資料異常");
      verify(codeRepository, never()).markAsRedeemedIfAvailable(anyLong(), anyLong(), any());
      verify(productRedemptionTransactionService, never())
          .recordTransaction(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("should reject when code becomes unavailable during atomic redeem")
    void shouldRejectWhenCodeBecomesUnavailableDuringAtomicRedeem() {
      // Given
      Instant now = Instant.now();
      Product product =
          new Product(
              Long.valueOf(TEST_PRODUCT_ID),
              TEST_GUILD_ID,
              "Test",
              null,
              null,
              null,
              null,
              now,
              now);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              null,
              null,
              now,
              null,
              1);

      when(codeRepository.findByCode("ABCD1234EFGH5678")).thenReturn(Optional.of(code));
      when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
      when(codeRepository.markAsRedeemedIfAvailable(eq(1L), eq(TEST_USER_ID), any()))
          .thenReturn(false);

      // When
      Result<RedemptionService.RedemptionResult, DomainError> result =
          redemptionService.redeemCode("ABCD1234EFGH5678", TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("已被使用或不可用");
      verify(productRedemptionTransactionService, never())
          .recordTransaction(anyLong(), anyLong(), any(), any());
      verify(eventPublisher, never()).publish(any());
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
      Product product =
          new Product(1L, TEST_GUILD_ID, "VIP 服務", "專人服務", null, null, null, now, now);
      RedemptionCode code =
          new RedemptionCode(
              1L, "ABCD1234EFGH5678", 1L, TEST_GUILD_ID, null, TEST_USER_ID, now, now, null, 1);

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
      Product product =
          new Product(
              1L, TEST_GUILD_ID, "禮包", null, Product.RewardType.CURRENCY, 1000L, null, now, now);
      RedemptionCode code =
          new RedemptionCode(
              1L, "ABCD1234EFGH5678", 1L, TEST_GUILD_ID, null, TEST_USER_ID, now, now, null, 1);

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
