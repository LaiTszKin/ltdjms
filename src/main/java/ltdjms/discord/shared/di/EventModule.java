package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.shared.events.DomainEventPublisher;

import javax.inject.Singleton;

/**
 * Dagger module providing event system dependencies.
 */
@Module
public class EventModule {

    @Provides
    @Singleton
    public DomainEventPublisher provideDomainEventPublisher() {
        return new DomainEventPublisher();
    }
}
