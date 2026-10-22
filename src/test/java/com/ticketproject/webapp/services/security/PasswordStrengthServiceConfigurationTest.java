package com.ticketproject.webapp.services.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ticketproject.webapp.constants.AppConstants;

/**
 * Configuration tests for {@link PasswordStrengthService}, verifying how the
 * {@code app.password.min-strength-score} property is bound into the service:
 * the documented default when the property is missing, an explicit override,
 * and the fail-fast behaviour for a value outside zxcvbn's {@code 0..4} range.
 *
 * <p>{@link ApplicationContextRunner} is used instead of a full application
 * context so that these tests only pay for the bean under test.</p>
 */
class PasswordStrengthServiceConfigurationTest
{
    /** zxcvbn score 2: below the default minimum, so it must be rejected. */
    private static final String WEAK_PASSWORD = "securePassword123";

    /** zxcvbn score 4: above the default minimum, so it must be accepted. */
    private static final String STRONG_PASSWORD = "Qz7!mN2#vKp9$Lx4";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PasswordStrengthService.class);

    @Test
    @DisplayName("A missing property falls back to the default minimum strength score")
    void missingPropertyFallsBackToDefault()
    {
        this.contextRunner.run(context ->
        {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PasswordStrengthService.class).getMinStrengthScore())
                .isEqualTo(AppConstants.Passwords.DEFAULT_MIN_STRENGTH_SCORE)
                .isEqualTo(3);
        });
    }

    @Test
    @DisplayName("An explicit property overrides the default minimum strength score")
    void explicitPropertyOverridesDefault()
    {
        this.contextRunner
            .withPropertyValues("app.password.min-strength-score=4")
            .run(context ->
            {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(PasswordStrengthService.class).getMinStrengthScore()).isEqualTo(4);
            });
    }

    @Test
    @DisplayName("The property can relax the minimum strength score")
    void propertyCanRelaxMinimum()
    {
        this.contextRunner
            .withPropertyValues("app.password.min-strength-score=0")
            .run(context ->
            {
                PasswordStrengthService service = context.getBean(PasswordStrengthService.class);

                assertThat(service.getMinStrengthScore()).isZero();
                assertThat(service.isStrongEnough(WEAK_PASSWORD)).isTrue();
            });
    }

    @Test
    @DisplayName("The bound threshold rejects passwords scoring below it")
    void boundThresholdRejectsWeakPasswords()
    {
        this.contextRunner.run(context ->
        {
            PasswordStrengthService service = context.getBean(PasswordStrengthService.class);

            assertThat(service.isStrongEnough(STRONG_PASSWORD)).isTrue();
            assertThat(service.isStrongEnough(WEAK_PASSWORD)).isFalse();
        });
    }

    @Test
    @DisplayName("A threshold above zxcvbn's maximum score prevents startup")
    void thresholdAboveMaximumPreventsStartup()
    {
        this.contextRunner
            .withPropertyValues("app.password.min-strength-score=5")
            .run(context ->
            {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.password.min-strength-score must be between 0 and 4");
            });
    }

    @Test
    @DisplayName("A negative threshold prevents startup")
    void negativeThresholdPreventsStartup()
    {
        this.contextRunner
            .withPropertyValues("app.password.min-strength-score=-1")
            .run(context ->
            {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.password.min-strength-score must be between 0 and 4");
            });
    }
}