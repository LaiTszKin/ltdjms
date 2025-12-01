package ltdjms.discord.shared.di;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppComponentLoadTest {

    @Test
    void shouldLoadAppComponentAndDaggerAppComponent() throws Exception {
        Class<?> envClass = Class.forName("ltdjms.discord.shared.EnvironmentConfig");
        Class<?> appClass = Class.forName("ltdjms.discord.shared.di.AppComponent");
        Class<?> daggerClass = Class.forName("ltdjms.discord.shared.di.DaggerAppComponent");
        Class<?> factoryClass = Class.forName("ltdjms.discord.shared.di.AppComponentFactory");

        assertThat(envClass).isNotNull();
        assertThat(appClass).isNotNull();
        assertThat(daggerClass).isNotNull();
        assertThat(factoryClass).isNotNull();
    }
}
