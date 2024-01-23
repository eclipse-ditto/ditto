/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.thingsearch.service.common.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

public final class DefaultNamespaceSearchIndexConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultNamespaceSearchIndexConfig.class, areImmutable(),
                          provided(String.class).isAlsoImmutable(),
                          assumingFields(
                                  "search-include-fields").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultNamespaceSearchIndexConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {

        final Config searchConfig = ConfigFactory.load("search-test");
        final DittoSearchConfig underTest = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(searchConfig));


        softly.assertThat(underTest.getNamespaceSearchIncludeFields()).isNotNull();

        softly.assertThat(underTest.getNamespaceSearchIncludeFields()).isEmpty();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {

        final Config searchConfig = ConfigFactory.load("namespace-search-index-test");
        final DittoSearchConfig underTest = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(searchConfig));

        softly.assertThat(underTest.getNamespaceSearchIncludeFields()).isNotNull();

        softly.assertThat(underTest.getNamespaceSearchIncludeFields()).isNotEmpty();

        softly.assertThat(underTest.getNamespaceSearchIncludeFields().size()).isEqualTo(2);

        NamespaceSearchIndexConfig first = underTest.getNamespaceSearchIncludeFields().get(0);
        NamespaceSearchIndexConfig second = underTest.getNamespaceSearchIncludeFields().get(1);

        // First config
        softly.assertThat(first.getNamespacePattern()).isEqualTo("org.eclipse");

        softly.assertThat(first.getSearchIncludeFields())
                .as(NamespaceSearchIndexConfig.NamespaceSearchIndexConfigValue.SEARCH_INCLUDE_FIELDS.getConfigPath())
                .isEqualTo(
                        List.of("attributes", "features/info"));

        // Second config
        softly.assertThat(second.getNamespacePattern()).isEqualTo("org.eclipse.test");

        softly.assertThat(second.getSearchIncludeFields())
                .as(NamespaceSearchIndexConfig.NamespaceSearchIndexConfigValue.SEARCH_INCLUDE_FIELDS.getConfigPath())
                .isEqualTo(
                        List.of("attributes", "features/info/properties/", "features/info/other"));
    }
}
