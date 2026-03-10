package ltdjms.discord.shared.di;

import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.Multibinds;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Dagger module providing event system dependencies. */
@Module
public abstract class EventModule {

  @Multibinds
  abstract Set<Consumer<DomainEvent>> domainEventListeners();

  @Provides
  @Singleton
  static DomainEventPublisher provideDomainEventPublisher(Set<Consumer<DomainEvent>> listeners) {
    return new DomainEventPublisher(listeners);
  }
}
