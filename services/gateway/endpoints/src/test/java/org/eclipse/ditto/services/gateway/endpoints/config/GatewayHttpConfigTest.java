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
package org.eclipse.ditto.services.gateway.endpoints.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.gateway.util.config.endpoints.GatewayHttpConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.util.config.endpoints.GatewayHttpConfig}.
 */
public final class GatewayHttpConfigTest {

    private static Config gatewayHttpTestConfig;

    @BeforeClass
    public static void setUp() {
        gatewayHttpTestConfig = ConfigFactory.load("gateway-http-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(GatewayHttpConfig.class,
                areImmutable(),
                provided(Pattern.class).isAlsoImmutable(),
                assumingFields("queryParamsAsHeaders", "schemaVersions")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GatewayHttpConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getSchemaVersionsReturnsDefaultIfNotSet() {
        final Set<JsonSchemaVersion> expected = getDefaultSchemaVersions();
        final GatewayHttpConfig underTest = GatewayHttpConfig.of(ConfigFactory.empty());

        final Set<JsonSchemaVersion> actual = underTest.getSupportedSchemaVersions();

        assertThat(actual).isEqualTo(expected);
    }

    private static Set<JsonSchemaVersion> getDefaultSchemaVersions() {
        final Object defaultValue = HttpConfig.GatewayHttpConfigValue.SCHEMA_VERSIONS.getDefaultValue();
        @SuppressWarnings("unchecked") final Collection<Integer> versionNumbers = (Collection<Integer>) defaultValue;
        return versionNumbers.stream()
                .map(JsonSchemaVersion::forInt)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    @Test
    public void getConfiguredSchemaVersions() {
        final EnumSet<JsonSchemaVersion> expected = EnumSet.of(JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);
        final GatewayHttpConfig underTest = GatewayHttpConfig.of(gatewayHttpTestConfig);

        final Set<JsonSchemaVersion> actual = underTest.getSupportedSchemaVersions();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryToGetInstanceWithUnknownSchemaVersions() {
        final int unknownVersionNumber = -1;
        final Collection<Integer> knownSchemaVersions =
                List.of(JsonSchemaVersion.V_1.toInt(), JsonSchemaVersion.V_2.toInt());
        final Collection<Integer> allSchemaVersions = new ArrayList<>(knownSchemaVersions);
        allSchemaVersions.add(unknownVersionNumber);
        final String configPath = "http." + HttpConfig.GatewayHttpConfigValue.SCHEMA_VERSIONS.getConfigPath();
        final Config config = ConfigFactory.parseMap(Maps.newHashMap(configPath, allSchemaVersions));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> GatewayHttpConfig.of(config))
                .withMessage("Schema version <%s> is not supported!", unknownVersionNumber)
                .withNoCause();
    }

    @Test
    public void getQueryParametersAsHeadersReturnsDefaultIfNotSet() {
        final Set<HeaderDefinition> expected = getDefaultQueryParamsAsHeaders();
        final GatewayHttpConfig underTest = GatewayHttpConfig.of(ConfigFactory.empty());

        final Set<HeaderDefinition> actual = underTest.getQueryParametersAsHeaders();

        assertThat(actual).isEqualTo(expected);
    }

    private static Set<HeaderDefinition> getDefaultQueryParamsAsHeaders() {
        final Object defaultValue = HttpConfig.GatewayHttpConfigValue.QUERY_PARAMS_AS_HEADERS.getDefaultValue();
        @SuppressWarnings("unchecked") final Collection<String> headerKeys = (Collection<String>) defaultValue;
        return headerKeys.stream()
                .map(DittoHeaderDefinition::forKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    @Test
    public void getConfiguredQueryParametersAsHeaders() {
        final Set<DittoHeaderDefinition> expected =
                EnumSet.of(DittoHeaderDefinition.REQUESTED_ACKS, DittoHeaderDefinition.TIMEOUT);
        final GatewayHttpConfig underTest = GatewayHttpConfig.of(gatewayHttpTestConfig);

        final Set<HeaderDefinition> actual = underTest.getQueryParametersAsHeaders();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryToGetInstanceWithUnknownQueryParametersAsHeaders() {
        final List<String> unknownHeaderKeys = Lists.newArrayList("foo", "bar");
        final List<String> knownHeaderKeys = Lists.newArrayList(DittoHeaderDefinition.CORRELATION_ID.getKey(),
                DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
        final List<String> allHeaderKeys = new ArrayList<>(knownHeaderKeys);
        allHeaderKeys.addAll(unknownHeaderKeys);
        final String configPath = "http." + HttpConfig.GatewayHttpConfigValue.QUERY_PARAMS_AS_HEADERS.getConfigPath();
        final Config config = ConfigFactory.parseMap(Maps.newHashMap(configPath, allHeaderKeys));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> GatewayHttpConfig.of(config))
                .withMessage("The query parameter names <%s> do not denote known header keys!", unknownHeaderKeys)
                .withNoCause();
    }

}