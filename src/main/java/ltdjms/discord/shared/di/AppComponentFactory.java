package ltdjms.discord.shared.di;

import ltdjms.discord.shared.EnvironmentConfig;

/**
 * Factory for creating the main Dagger {@link AppComponent}.
 * Centralizes the wiring logic so both production code and tests
 * can build the component in a consistent way.
 */
public final class AppComponentFactory {

    private AppComponentFactory() {
        // Utility class; do not instantiate.
    }

    /**
     * Creates a new {@link AppComponent} instance using the given environment configuration.
     *
     * @param envConfig the environment configuration
     * @return a fully wired Dagger AppComponent
     */
    public static AppComponent create(EnvironmentConfig envConfig) {
        return DaggerAppComponent.builder()
                .databaseModule(new DatabaseModule(envConfig))
                .build();
    }
}

