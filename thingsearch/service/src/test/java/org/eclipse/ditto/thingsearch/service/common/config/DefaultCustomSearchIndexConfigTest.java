/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.CustomSearchIndexConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.CustomSearchIndexFieldConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.DefaultCustomSearchIndexConfig;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link DefaultCustomSearchIndexConfig}.
 */
public final class DefaultCustomSearchIndexConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final Config searchConfig = ConfigFactory.load("search-test");
        final DittoSearchConfig underTest = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(searchConfig));

        softly.assertThat(underTest.getCustomIndexes()).isNotNull();
        softly.assertThat(underTest.getCustomIndexes()).isEmpty();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final Config searchConfig = ConfigFactory.load("custom-search-index-test");
        final DittoSearchConfig underTest = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(searchConfig));

        softly.assertThat(underTest.getCustomIndexes()).isNotNull();
        softly.assertThat(underTest.getCustomIndexes()).hasSize(3);

        // First index: my_custom_idx
        final var firstIndex = underTest.getCustomIndexes().get("my_custom_idx");
        softly.assertThat(firstIndex).isNotNull();
        softly.assertThat(firstIndex.getName()).isEqualTo("my_custom_idx");
        softly.assertThat(firstIndex.getFields()).hasSize(2);
        softly.assertThat(firstIndex.getFields().get(0).getName()).isEqualTo("t.attributes.region");
        softly.assertThat(firstIndex.getFields().get(0).getDirection())
                .isEqualTo(CustomSearchIndexFieldConfig.Direction.ASC);
        softly.assertThat(firstIndex.getFields().get(1).getName()).isEqualTo("t.attributes.timestamp");
        softly.assertThat(firstIndex.getFields().get(1).getDirection())
                .isEqualTo(CustomSearchIndexFieldConfig.Direction.DESC);

        // Second index: another_index
        final var secondIndex = underTest.getCustomIndexes().get("another_index");
        softly.assertThat(secondIndex).isNotNull();
        softly.assertThat(secondIndex.getName()).isEqualTo("another_index");
        softly.assertThat(secondIndex.getFields()).hasSize(2);
        softly.assertThat(secondIndex.getFields().get(0).getName()).isEqualTo("_namespace");
        softly.assertThat(secondIndex.getFields().get(1).getName()).isEqualTo("t.features.sensor.properties.value");
        // Lower-case 'desc' should be parsed correctly
        softly.assertThat(secondIndex.getFields().get(1).getDirection())
                .isEqualTo(CustomSearchIndexFieldConfig.Direction.DESC);

        // Third index: single_field_index with default direction
        final var thirdIndex = underTest.getCustomIndexes().get("single_field_index");
        softly.assertThat(thirdIndex).isNotNull();
        softly.assertThat(thirdIndex.getFields()).hasSize(1);
        softly.assertThat(thirdIndex.getFields().get(0).getName()).isEqualTo("t.policyId");
        softly.assertThat(thirdIndex.getFields().get(0).getDirection())
                .isEqualTo(CustomSearchIndexFieldConfig.Direction.ASC);
    }

    @Test
    public void allOfKeepsOrderAndRejectsEmptyFields() {
        final var ordered = ConfigFactory.parseString("""
                zeta { fields = [ { name = "a" } ] }
                alpha { fields = [ { name = "b", direction = "DESC" } ] }
                mid { fields = [ { name = "c" } ] }
                """).root();

        final Map<String, CustomSearchIndexConfig> underTest = DefaultCustomSearchIndexConfig.allOf(ordered);

        // the entry order of the ConfigObject (typesafe config's entrySet() does not keep the source order) is kept:
        softly.assertThat(underTest.keySet())
                .containsExactlyElementsOf(ordered.entrySet().stream().map(Map.Entry::getKey).toList());
        softly.assertThat(underTest.get("alpha").getName()).isEqualTo("alpha");
        softly.assertThat(underTest.get("alpha").getFields().get(0).getDirection())
                .isEqualTo(CustomSearchIndexFieldConfig.Direction.DESC);
        softly.assertThatThrownBy(() -> underTest.put("other", underTest.get("mid")))
                .as("unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);

        final var withEmpty = ConfigFactory.parseString("""
                control { fields = [ { name = "a" } ] }
                broken { fields = [] }
                """).root();
        softly.assertThatThrownBy(() -> DefaultCustomSearchIndexConfig.allOf(withEmpty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'broken' must have at least one field");
        softly.assertThat(DefaultCustomSearchIndexConfig.allOf(ConfigFactory.parseString("""
                control { fields = [ { name = "a" } ] }
                """).root()))
                .as("control entry parses")
                .containsOnlyKeys("control");
    }

    @Test
    public void parseIndexWithEmptyFieldsFails() {
        final var config = ConfigFactory.parseString("""
                fields = []
                """);

        assertThatThrownBy(() -> DefaultCustomSearchIndexConfig.of("empty_index", config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one field");
    }

    @Test
    public void parseIndexWithEmptyNameFails() {
        final var config = ConfigFactory.parseString("""
                fields = [
                    { name = "t.attributes/foo", direction = "ASC" }
                ]
                """);

        assertThatThrownBy(() -> DefaultCustomSearchIndexConfig.of("", config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be empty");
    }
}
