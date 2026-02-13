package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.dispatch.commands.DispatchPanelCommandHandler;
import ltdjms.discord.dispatch.commands.DispatchPanelInteractionHandler;
import ltdjms.discord.dispatch.domain.EscortDispatchOrderRepository;
import ltdjms.discord.dispatch.persistence.JdbcEscortDispatchOrderRepository;
import ltdjms.discord.dispatch.services.EscortDispatchOrderService;

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
  public DispatchPanelCommandHandler provideDispatchPanelCommandHandler() {
    return new DispatchPanelCommandHandler();
  }

  @Provides
  @Singleton
  public DispatchPanelInteractionHandler provideDispatchPanelInteractionHandler(
      EscortDispatchOrderService orderService) {
    return new DispatchPanelInteractionHandler(orderService);
  }
}
