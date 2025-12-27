package ltdjms.discord.shared.di;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Dagger module providing event system dependencies. */
@Module
public class EventModule {

  @Provides
  @Singleton
  public DomainEventPublisher provideDomainEventPublisher() {
    return new DomainEventPublisher();
  }
}
