package org.eclipse.ditto.services.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Basic test for {@link ConditionalHeadersValidator}. The concrete functionality is tested in context of the using
 * service.
 */
public class ConditionalHeadersValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConditionalHeadersValidator.class, areImmutable(),
                provided(ConditionalHeadersValidator.ValidationSettings.class).isAlsoImmutable());
    }

    @Test
    public void creationFailsWithNullValidationSettings() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConditionalHeadersValidator.of(null));
    }
}
