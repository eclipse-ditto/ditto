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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link DefaultCustomSearchIndexFieldConfig}.
 */
public final class DefaultCustomSearchIndexFieldConfigTest {

    @Test
    public void parseFieldWithAscDirection() {
        final var config = ConfigFactory.parseString("""
                name = "t.attributes/region"
                direction = "ASC"
                """);

        final var underTest = DefaultCustomSearchIndexFieldConfig.of(config);

        assertThat(underTest.getName()).isEqualTo("t.attributes/region");
        assertThat(underTest.getDirection()).isEqualTo(CustomSearchIndexFieldConfig.Direction.ASC);
    }

    @Test
    public void parseFieldWithDescDirection() {
        final var config = ConfigFactory.parseString("""
                name = "_modified"
                direction = "DESC"
                """);

        final var underTest = DefaultCustomSearchIndexFieldConfig.of(config);

        assertThat(underTest.getName()).isEqualTo("_modified");
        assertThat(underTest.getDirection()).isEqualTo(CustomSearchIndexFieldConfig.Direction.DESC);
    }

    @Test
    public void parseFieldWithLowerCaseDirection() {
        final var config = ConfigFactory.parseString("""
                name = "t.policyId"
                direction = "desc"
                """);

        final var underTest = DefaultCustomSearchIndexFieldConfig.of(config);

        assertThat(underTest.getName()).isEqualTo("t.policyId");
        assertThat(underTest.getDirection()).isEqualTo(CustomSearchIndexFieldConfig.Direction.DESC);
    }

    @Test
    public void parseFieldWithDefaultDirection() {
        final var config = ConfigFactory.parseString("""
                name = "t.attributes/foo"
                """);

        final var underTest = DefaultCustomSearchIndexFieldConfig.of(config);

        assertThat(underTest.getName()).isEqualTo("t.attributes/foo");
        assertThat(underTest.getDirection()).isEqualTo(CustomSearchIndexFieldConfig.Direction.ASC);
    }

    @Test
    public void parseFieldWithEmptyNameFails() {
        final var config = ConfigFactory.parseString("""
                name = ""
                direction = "ASC"
                """);

        assertThatThrownBy(() -> DefaultCustomSearchIndexFieldConfig.of(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    public void directionMongoValuesAreCorrect() {
        assertThat(CustomSearchIndexFieldConfig.Direction.ASC.getMongoValue()).isEqualTo(1);
        assertThat(CustomSearchIndexFieldConfig.Direction.DESC.getMongoValue()).isEqualTo(-1);
    }
}
