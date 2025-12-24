package ltdjms.discord.redemption.services;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.RedemptionCodesGeneratedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 驗證兌換碼批次生成會發布事件以觸發面板更新。
 */
class RedemptionServiceEventTest {

    private RedemptionService redemptionService;
    private RedemptionCodeRepository codeRepository;
    private ProductRepository productRepository;
    private RedemptionCodeGenerator codeGenerator;
    private DomainEventPublisher eventPublisher;

    private final long productId = 123L;
    private final long guildId = 456L;

    @BeforeEach
    void setUp() {
        codeRepository = mock(RedemptionCodeRepository.class);
        productRepository = mock(ProductRepository.class);
        codeGenerator = mock(RedemptionCodeGenerator.class);
        eventPublisher = mock(DomainEventPublisher.class);

        redemptionService = new RedemptionService(
                codeRepository,
                productRepository,
                codeGenerator,
                mock(ltdjms.discord.currency.services.BalanceAdjustmentService.class),
                mock(ltdjms.discord.gametoken.services.GameTokenService.class),
                mock(ltdjms.discord.currency.services.CurrencyTransactionService.class),
                mock(ltdjms.discord.gametoken.services.GameTokenTransactionService.class),
                mock(ProductRedemptionTransactionService.class),
                eventPublisher
        );
    }

    @Test
    void generateCodes_shouldPublishEventOnSuccess() {
        Product product = new Product(productId, guildId, "P1", null, null, null, Instant.now(), Instant.now());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(codeGenerator.generate()).thenReturn("ABCDEFGH");
        when(codeRepository.existsByCode("ABCDEFGH")).thenReturn(false);
        when(codeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Result<List<RedemptionCode>, DomainError> result = redemptionService.generateCodes(productId, 1, null);

        assertThat(result.isOk()).isTrue();
        verify(eventPublisher).publish(any(RedemptionCodesGeneratedEvent.class));
    }
}
