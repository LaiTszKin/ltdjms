package ltdjms.discord.dispatch.services;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.domain.EscortDispatchOrderRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** 派單護航訂單核心服務。 */
public class EscortDispatchOrderService {

  private static final Logger LOG = LoggerFactory.getLogger(EscortDispatchOrderService.class);

  static final int MAX_ORDER_NUMBER_RETRIES = 20;

  private final EscortDispatchOrderRepository repository;
  private final EscortDispatchOrderNumberGenerator orderNumberGenerator;

  public EscortDispatchOrderService(EscortDispatchOrderRepository repository) {
    this(repository, new EscortDispatchOrderNumberGenerator());
  }

  public EscortDispatchOrderService(
      EscortDispatchOrderRepository repository,
      EscortDispatchOrderNumberGenerator orderNumberGenerator) {
    this.repository = repository;
    this.orderNumberGenerator = orderNumberGenerator;
  }

  /** 建立待確認的新派單訂單。 */
  public Result<EscortDispatchOrder, DomainError> createOrder(
      long guildId, long assignedByUserId, long escortUserId, long customerUserId) {

    if (escortUserId == customerUserId) {
      return Result.err(DomainError.invalidInput("護航者與客戶不能是同一人"));
    }

    try {
      String orderNumber = generateUniqueOrderNumber();
      EscortDispatchOrder order =
          EscortDispatchOrder.createPending(
              orderNumber, guildId, assignedByUserId, escortUserId, customerUserId);

      EscortDispatchOrder saved = repository.save(order);
      LOG.info(
          "Created escort dispatch order: orderNumber={}, guildId={}, escortUserId={},"
              + " customerUserId={}",
          saved.orderNumber(),
          guildId,
          escortUserId,
          customerUserId);
      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error(
          "Failed to create escort dispatch order: guildId={}, escortUserId={}, customerUserId={}",
          guildId,
          escortUserId,
          customerUserId,
          e);
      return Result.err(DomainError.persistenceFailure("建立派單失敗", e));
    }
  }

  /** 護航者確認接單。 */
  public Result<EscortDispatchOrder, DomainError> confirmOrder(
      String orderNumber, long confirmerUserId) {
    if (orderNumber == null || orderNumber.isBlank()) {
      return Result.err(DomainError.invalidInput("訂單編號無效"));
    }

    String normalizedOrderNumber = orderNumber.trim().toUpperCase();

    Optional<EscortDispatchOrder> orderOpt;
    try {
      orderOpt = repository.findByOrderNumber(normalizedOrderNumber);
    } catch (Exception e) {
      LOG.error("Failed to query escort dispatch order: orderNumber={}", normalizedOrderNumber, e);
      return Result.err(DomainError.persistenceFailure("查詢訂單失敗", e));
    }

    if (orderOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到該訂單"));
    }

    EscortDispatchOrder order = orderOpt.get();

    if (!order.canBeConfirmedBy(confirmerUserId)) {
      return Result.err(DomainError.invalidInput("只有被指派的護航者可以確認此訂單"));
    }

    if (!order.isPendingConfirmation()) {
      return Result.err(DomainError.invalidInput("此訂單已確認"));
    }

    try {
      EscortDispatchOrder confirmed = order.withConfirmed(Instant.now());
      EscortDispatchOrder updated = repository.update(confirmed);
      LOG.info(
          "Escort dispatch order confirmed: orderNumber={}, escortUserId={}",
          updated.orderNumber(),
          confirmerUserId);
      return Result.ok(updated);
    } catch (Exception e) {
      LOG.error(
          "Failed to confirm escort dispatch order: orderNumber={}, confirmerUserId={}",
          normalizedOrderNumber,
          confirmerUserId,
          e);
      return Result.err(DomainError.persistenceFailure("確認訂單失敗", e));
    }
  }

  /** 依訂單編號查詢。 */
  public Optional<EscortDispatchOrder> findByOrderNumber(String orderNumber) {
    if (orderNumber == null || orderNumber.isBlank()) {
      return Optional.empty();
    }
    return repository.findByOrderNumber(orderNumber.trim().toUpperCase());
  }

  private String generateUniqueOrderNumber() {
    for (int i = 0; i < MAX_ORDER_NUMBER_RETRIES; i++) {
      String candidate = orderNumberGenerator.generate();
      if (!repository.existsByOrderNumber(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Unable to generate unique order number after retries");
  }
}
