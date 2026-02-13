package ltdjms.discord.dispatch.domain;

import java.util.Optional;

/** 派單護航訂單的持久化介面。 */
public interface EscortDispatchOrderRepository {

  /** 儲存新訂單並回傳帶有資料庫主鍵的實體。 */
  EscortDispatchOrder save(EscortDispatchOrder order);

  /** 更新既有訂單並回傳最新狀態。 */
  EscortDispatchOrder update(EscortDispatchOrder order);

  /** 依訂單編號查詢。 */
  Optional<EscortDispatchOrder> findByOrderNumber(String orderNumber);

  /** 檢查訂單編號是否已存在。 */
  boolean existsByOrderNumber(String orderNumber);
}
