package ltdjms.discord.product.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.ProductChangedEvent;

class ProductServiceEventTest {

  private ProductRepository productRepository;
  private RedemptionCodeRepository redemptionCodeRepository;
  private DomainEventPublisher eventPublisher;
  private ProductService service;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    redemptionCodeRepository = mock(RedemptionCodeRepository.class);
    eventPublisher = mock(DomainEventPublisher.class);
    service = new ProductService(productRepository, redemptionCodeRepository, eventPublisher);
  }

  @Test
  void createProduct_shouldPublishEventOnSuccess() {
    // Given
    long guildId = 123L;
    String name = "測試商品";
    String description = "這是描述";
    Product.RewardType rewardType = Product.RewardType.CURRENCY;
    Long rewardAmount = 100L;

    when(productRepository.existsByGuildIdAndName(guildId, name)).thenReturn(false);
    when(productRepository.save(any(Product.class)))
        .thenAnswer(
            invocation -> {
              Product p = invocation.getArgument(0);
              return new Product(
                  1L,
                  p.guildId(),
                  p.name(),
                  p.description(),
                  p.rewardType(),
                  p.rewardAmount(),
                  p.currencyPrice(),
                  p.createdAt(),
                  p.updatedAt());
            });

    // When
    Result<Product, ?> result =
        service.createProduct(guildId, name, description, rewardType, rewardAmount, null);

    // Then
    assertThat(result.isOk()).isTrue();

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());

    DomainEvent event = eventCaptor.getValue();
    assertThat(event).isInstanceOf(ProductChangedEvent.class);
    ProductChangedEvent productEvent = (ProductChangedEvent) event;
    assertThat(productEvent.guildId()).isEqualTo(guildId);
    assertThat(productEvent.productId()).isEqualTo(1L);
    assertThat(productEvent.operationType()).isEqualTo(ProductChangedEvent.OperationType.CREATED);
  }

  @Test
  void updateProduct_shouldPublishEventOnSuccess() {
    // Given
    long productId = 1L;
    long guildId = 123L;
    String newName = "更新後的商品";
    String newDescription = "更新後的描述";

    Product existingProduct =
        new Product(
            productId,
            guildId,
            "原始商品",
            "原始描述",
            Product.RewardType.CURRENCY,
            100L,
            null,
            Instant.now(),
            Instant.now());

    when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
    when(productRepository.existsByGuildIdAndNameExcludingId(anyLong(), anyString(), anyLong()))
        .thenReturn(false);
    when(productRepository.update(any(Product.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Result<Product, ?> result =
        service.updateProduct(
            productId, newName, newDescription, Product.RewardType.CURRENCY, 150L, null);

    // Then
    assertThat(result.isOk()).isTrue();

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());

    ProductChangedEvent event = (ProductChangedEvent) eventCaptor.getValue();
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.productId()).isEqualTo(productId);
    assertThat(event.operationType()).isEqualTo(ProductChangedEvent.OperationType.UPDATED);
  }

  @Test
  void deleteProduct_shouldPublishEventOnSuccess() {
    // Given
    long productId = 1L;
    long guildId = 123L;

    Product existingProduct =
        new Product(
            productId, guildId, "待刪除商品", null, null, null, null, Instant.now(), Instant.now());

    when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
    when(redemptionCodeRepository.invalidateByProductId(productId)).thenReturn(0);
    when(productRepository.deleteById(productId)).thenReturn(true);

    // When
    Result<Unit, ?> result = service.deleteProduct(productId);

    // Then
    assertThat(result.isOk()).isTrue();

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());

    ProductChangedEvent event = (ProductChangedEvent) eventCaptor.getValue();
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.productId()).isEqualTo(productId);
    assertThat(event.operationType()).isEqualTo(ProductChangedEvent.OperationType.DELETED);
  }

  @Test
  void createProduct_shouldNotPublishEventOnValidationFailure() {
    // Given
    long guildId = 123L;
    String invalidName = "";

    // When
    Result<Product, ?> result = service.createProduct(guildId, invalidName, null, null, null, null);

    // Then
    assertThat(result.isErr()).isTrue();
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void createProduct_shouldNotPublishEventOnDuplicateName() {
    // Given
    long guildId = 123L;
    String name = "已存在的商品";

    when(productRepository.existsByGuildIdAndName(guildId, name)).thenReturn(true);

    // When
    Result<Product, ?> result = service.createProduct(guildId, name, null, null, null, null);

    // Then
    assertThat(result.isErr()).isTrue();
    verify(eventPublisher, never()).publish(any());
  }
}
