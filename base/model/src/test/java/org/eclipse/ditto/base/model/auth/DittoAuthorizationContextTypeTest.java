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
package org.eclipse.ditto.base.model.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType}.
 */
public final class DittoAuthorizationContextTypeTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoAuthorizationContextType.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoAuthorizationContextType.class)
                .verify();

        final AuthorizationContextType underTest = DittoAuthorizationContextType.JWT;
        final AuthorizationContextType other = ImmutableAuthorizationContextType.of(underTest.toString());

        assertThat((CharSequence) other).isEqualTo(underTest);
    }

    @Test
    public void containsNullIsFalse() {
        assertThat(DittoAuthorizationContextType.contains(null)).isFalse();
    }

    @Test
    public void doesNotContainGivenLabel() {
        final AuthorizationContextType authorizationContextType = ImmutableAuthorizationContextType.of("abc");

        assertThat(DittoAuthorizationContextType.contains(authorizationContextType)).isFalse();
    }

    @Test
    public void containsConstantOfSelf() {
        assertThat(DittoAuthorizationContextType.contains(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION)).isTrue();
    }

    @Test
    public void containsGivenLabel() {
        final AuthorizationContextType authorizationContextType =
                ImmutableAuthorizationContextType.of(DittoAuthorizationContextType.JWT.toString());

        assertThat(DittoAuthorizationContextType.contains(authorizationContextType)).isTrue();
    }

    @Test
    public void valuesReturnsAllConstantsInCorrectOrder() throws IllegalAccessException {
        final Collection<DittoAuthorizationContextType> expectedConstants = new ArrayList<>();
        final Class<DittoAuthorizationContextType> underTestType = DittoAuthorizationContextType.class;
        final Field[] declaredFields = underTestType.getDeclaredFields();
        for (final Field declaredField : declaredFields) {
            if (isConstant(underTestType, declaredField)) {
                final Object constantValue = declaredField.get(null);
                expectedConstants.add((DittoAuthorizationContextType) constantValue);
            }
        }

        final AuthorizationContextType[] actual = DittoAuthorizationContextType.values();

        assertThat(actual).containsExactlyElementsOf(expectedConstants);
    }

    private static boolean isConstant(final Class<?> underTestType, final Field declaredField) {
        boolean result = false;
        if (underTestType.equals(declaredField.getType())) {
            final int modifiers = declaredField.getModifiers();
            result = Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && Modifier.isFinal(modifiers);
        }
        return result;
    }

}
