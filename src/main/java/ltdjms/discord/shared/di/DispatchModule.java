package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.dispatch.commands.DispatchPanelCommandHandler;
import ltdjms.discord.dispatch.commands.DispatchPanelInteractionHandler;
import ltdjms.discord.dispatch.domain.DispatchAfterSalesStaffRepository;
import ltdjms.discord.dispatch.domain.EscortDispatchOrderRepository;
import ltdjms.discord.dispatch.domain.EscortOptionPriceRepository;
import ltdjms.discord.dispatch.persistence.JdbcDispatchAfterSalesStaffRepository;
import ltdjms.discord.dispatch.persistence.JdbcEscortDispatchOrderRepository;
import ltdjms.discord.dispatch.persistence.JdbcEscortOptionPriceRepository;
import ltdjms.discord.dispatch.services.DispatchAfterSalesStaffService;
import ltdjms.discord.dispatch.services.EscortDispatchOrderService;
import ltdjms.discord.dispatch.services.EscortOptionPricingService;

/** Dagger module for escort dispatch system dependencies. */
@Module
public class DispatchModule {

  @Provides
  @Singleton
  public EscortDispatchOrderRepository provideEscortDispatchOrderRepository(DataSource dataSource) {
    return new JdbcEscortDispatchOrderRepository(dataSource);
  }

  @Provides
  @Singleton
  public EscortDispatchOrderService provideEscortDispatchOrderService(
      EscortDispatchOrderRepository repository) {
    return new EscortDispatchOrderService(repository);
  }

  @Provides
  @Singleton
  public DispatchAfterSalesStaffRepository provideDispatchAfterSalesStaffRepository(
      DataSource dataSource) {
    return new JdbcDispatchAfterSalesStaffRepository(dataSource);
  }

  @Provides
  @Singleton
  public DispatchAfterSalesStaffService provideDispatchAfterSalesStaffService(
      DispatchAfterSalesStaffRepository repository) {
    return new DispatchAfterSalesStaffService(repository);
  }

  @Provides
  @Singleton
  public EscortOptionPriceRepository provideEscortOptionPriceRepository(DataSource dataSource) {
    return new JdbcEscortOptionPriceRepository(dataSource);
  }

  @Provides
  @Singleton
  public EscortOptionPricingService provideEscortOptionPricingService(
      EscortOptionPriceRepository repository) {
    return new EscortOptionPricingService(repository);
  }

  @Provides
  @Singleton
  public DispatchPanelCommandHandler provideDispatchPanelCommandHandler() {
    return new DispatchPanelCommandHandler();
  }

  @Provides
  @Singleton
  public DispatchPanelInteractionHandler provideDispatchPanelInteractionHandler(
      EscortDispatchOrderService orderService,
      DispatchAfterSalesStaffService afterSalesStaffService) {
    return new DispatchPanelInteractionHandler(orderService, afterSalesStaffService);
  }
}
