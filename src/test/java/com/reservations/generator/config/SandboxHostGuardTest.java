package com.reservations.generator.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the REQ-8 startup guard actually rejects a {@code united.com}
 * hosted configuration and accepts a sandbox-hosted one.
 */
class SandboxHostGuardTest {

    @Test
    void directlyCallingTheGuardWithAProductionHostThrows() {
        SandboxProperties properties = new SandboxProperties();
        properties.setBaseUrl("https://www.united.com/reservations");
        SandboxHostGuard guard = new SandboxHostGuard(properties);

        assertThatThrownBy(guard::validateSandboxBaseUrlIsNeverProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("united.com");
    }

    @Test
    void directlyCallingTheGuardWithTheBareProductionHostThrows() {
        SandboxProperties properties = new SandboxProperties();
        properties.setBaseUrl("https://united.com/reservations");
        SandboxHostGuard guard = new SandboxHostGuard(properties);

        assertThatThrownBy(guard::validateSandboxBaseUrlIsNeverProduction)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void directlyCallingTheGuardWithASandboxHostDoesNotThrow() {
        SandboxProperties properties = new SandboxProperties();
        properties.setBaseUrl("https://sandbox.example.com");
        SandboxHostGuard guard = new SandboxHostGuard(properties);

        guard.validateSandboxBaseUrlIsNeverProduction();
    }

    @Test
    void applicationStartupFailsFastForAProductionHostedConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(GuardTestConfiguration.class)
                .withPropertyValues("reservations.sandbox.base-url=https://united.com/api")
                .run(this::assertStartupFailedWithUnitedComMessage);
    }

    @Test
    void applicationStartupSucceedsForASandboxHostedConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(GuardTestConfiguration.class)
                .withPropertyValues("reservations.sandbox.base-url=https://sandbox.example.com")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private void assertStartupFailedWithUnitedComMessage(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(context.getStartupFailure()).hasRootCauseMessage(
                "reservations.sandbox.base-url must never target a real united.com host; got: https://united.com/api");
    }

    @Configuration
    @EnableConfigurationProperties(SandboxProperties.class)
    static class GuardTestConfiguration {

        @Bean
        SandboxHostGuard sandboxHostGuard(SandboxProperties properties) {
            return new SandboxHostGuard(properties);
        }
    }
}
