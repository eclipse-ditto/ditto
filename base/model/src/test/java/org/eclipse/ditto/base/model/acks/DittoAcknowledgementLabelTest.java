/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.base.model.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel}.
 */
public final class DittoAcknowledgementLabelTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoAcknowledgementLabel.class,
                areImmutable(),
                provided(AcknowledgementLabel.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoAcknowledgementLabel.class)
                .usingGetClass()
                .verify();

        final DittoAcknowledgementLabel underTest = DittoAcknowledgementLabel.TWIN_PERSISTED;
        final AcknowledgementLabel other = AcknowledgementLabel.of(underTest.toString());

        assertThat((CharSequence) other).isEqualTo(underTest);
    }

    @Test
    public void containsNullIsFalse() {
        assertThat(DittoAcknowledgementLabel.contains(null)).isFalse();
    }

    @Test
    public void doesNotContainGivenLabel() {
        final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("abc");

        assertThat(DittoAcknowledgementLabel.contains(acknowledgementLabel)).isFalse();
    }

    @Test
    public void containsConstantOfSelf() {
        assertThat(DittoAcknowledgementLabel.contains(DittoAcknowledgementLabel.TWIN_PERSISTED)).isTrue();
    }

    @Test
    public void containsGivenLabel() {
        final AcknowledgementLabel acknowledgementLabel =
                AcknowledgementLabel.of(DittoAcknowledgementLabel.TWIN_PERSISTED.toString());

        assertThat(DittoAcknowledgementLabel.contains(acknowledgementLabel)).isTrue();
    }

    @Test
    public void valuesReturnsAllConstantsInCorrectOrder() throws IllegalAccessException {
        final Collection<DittoAcknowledgementLabel> expectedConstants = new ArrayList<>();
        final Class<DittoAcknowledgementLabel> underTestType = DittoAcknowledgementLabel.class;
        final Field[] declaredFields = underTestType.getDeclaredFields();
        for (final Field declaredField : declaredFields) {
            if (isDittoAckLabelConstant(underTestType, declaredField)) {
                final Object constantValue = declaredField.get(null);
                expectedConstants.add((DittoAcknowledgementLabel) constantValue);
            }
        }

        final AcknowledgementLabel[] actual = DittoAcknowledgementLabel.values();

        assertThat(actual).containsExactlyElementsOf(expectedConstants);
    }

    private static boolean isDittoAckLabelConstant(final Class<?> underTestType, final Field declaredField) {
        boolean result = false;
        if (underTestType.equals(declaredField.getType())) {
            final int modifiers = declaredField.getModifiers();
            result = Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && Modifier.isFinal(modifiers);
        }
        return result;
    }

}
