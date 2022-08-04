/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

import org.assertj.core.util.Maps;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link CheckExternalFilter}.
 */
public final class CheckExternalFilterTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(CheckExternalFilter.class,
                areImmutable(),
                provided(Predicate.class).isAlsoImmutable(),
                assumingFields("headerDefinitions").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void tryToGetShouldReadFromExternalInstanceWithNullHeaderDefinitions() {
        assertThatNullPointerException()
                .isThrownBy(() -> CheckExternalFilter.shouldReadFromExternal(null))
                .withMessage("The headerDefinitions must not be null!")
                .withNoCause();
    }

    @Test
    public void filterNullValueShouldReadFromExternal() {
        final Map<String, HeaderDefinition> headerDefinitions = Mockito.mock(Map.class);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldReadFromExternal(headerDefinitions);

        assertThat(underTest.apply("foo", null)).isNull();
        Mockito.verifyNoInteractions(headerDefinitions);
    }

    @Test
    public void filterValueWithoutMatchingHeaderDefinitionShouldBeReadFromExternal() {
        final String key = "foo";
        final String value = "bar";
        final CheckExternalFilter underTest = CheckExternalFilter.shouldReadFromExternal(Collections.emptyMap());

        assertThat(underTest.apply(key, value)).isEqualTo(value);
    }

    @Test
    public void filterValueThatShouldBeReadFromExternal() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.CORRELATION_ID;
        final String value = "correlation-id";
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldReadFromExternal(headerDefinitions);

        assertThat(underTest.apply(headerDefinition.getKey(), value)).isEqualTo(value);
    }

    @Test
    public void filterValueThatShouldNotBeReadFromExternal() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.DRY_RUN;
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldReadFromExternal(headerDefinitions);

        assertThat(underTest.apply(headerDefinition.getKey(), "true")).isNull();
    }

    @Test
    public void tryToGetShouldWriteToExternalInstanceWithNullHeaderDefinitions() {
        assertThatNullPointerException()
                .isThrownBy(() -> CheckExternalFilter.shouldWriteToExternal(null))
                .withMessage("The headerDefinitions must not be null!")
                .withNoCause();
    }

    @Test
    public void filterNullValueShouldWriteToExternal() {
        final Map<String, HeaderDefinition> headerDefinitions = Mockito.mock(Map.class);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldWriteToExternal(headerDefinitions);

        assertThat(underTest.apply("foo", null)).isNull();
        Mockito.verifyNoInteractions(headerDefinitions);
    }

    @Test
    public void filterValueWithoutMatchingHeaderDefinitionShouldWriteToExternal() {
        final String key = "foo";
        final String value = "bar";
        final CheckExternalFilter underTest = CheckExternalFilter.shouldWriteToExternal(Collections.emptyMap());

        assertThat(underTest.apply(key, value)).isEqualTo(value);
    }

    @Test
    public void filterValueThatShouldBeExposedExternally() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.CORRELATION_ID;
        final String value = "correlation-id";
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldWriteToExternal(headerDefinitions);

        assertThat(underTest.apply(headerDefinition.getKey(), value)).isEqualTo(value);
    }

    @Test
    public void filterValueThatShouldNotBeExposedExternally() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.DRY_RUN;
        final Map<String, HeaderDefinition> headerDefinitions =
                Maps.newHashMap(headerDefinition.getKey(), headerDefinition);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldWriteToExternal(headerDefinitions);

        assertThat(underTest.apply(headerDefinition.getKey(), "true")).isNull();
    }

    @Test
    public void filterShouldNotBeCaseSensitiveForDefinitionKey() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.WWW_AUTHENTICATE;

        doFilterShouldNotBeCaseSensitiveForDefinitionKey(headerDefinition, headerDefinition.getKey());
        doFilterShouldNotBeCaseSensitiveForDefinitionKey(headerDefinition, headerDefinition.getKey().toUpperCase());
        doFilterShouldNotBeCaseSensitiveForDefinitionKey(headerDefinition, "Www-authenticate");
    }

    private void doFilterShouldNotBeCaseSensitiveForDefinitionKey(final DittoHeaderDefinition headerDefinition,
            final String key) {

        final String value = "some www authenticate";
        final Map<String, HeaderDefinition> headerDefinitions = Maps.newHashMap(headerDefinition.getKey(),
                headerDefinition);
        final CheckExternalFilter underTest = CheckExternalFilter.shouldReadFromExternal(headerDefinitions);

        // DittoHeaderDefinition.WWW_AUTHENTICATE may not be read from external
        assertThat(underTest.apply(key, value)).withFailMessage(
                "For key '" + key + "' the mapped value must be null").isNull();
    }
}
