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
package org.eclipse.ditto.timeseries.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultMongoDbTimeseriesAdapterConfig}.
 */
public final class DefaultMongoDbTimeseriesAdapterConfigTest {

    private static final String URI = "mongodb://localhost:27017";

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(DefaultMongoDbTimeseriesAdapterConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithGivenValues() {
        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(URI, "custom_db", "x_", Granularity.MINUTES);

        assertThat(underTest.getUri()).isEqualTo(URI);
        assertThat(underTest.getDatabase()).isEqualTo("custom_db");
        assertThat(underTest.getCollectionPrefix()).isEqualTo("x_");
        assertThat(underTest.getGranularity()).isEqualTo(Granularity.MINUTES);
    }

    @Test
    public void factoryRejectsNullUri() {
        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(null, "db", "ts_", Granularity.SECONDS));
    }

    @Test
    public void factoryRejectsNullDatabase() {
        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(URI, null, "ts_", Granularity.SECONDS));
    }

    @Test
    public void factoryRejectsNullCollectionPrefix() {
        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(URI, "db", null, Granularity.SECONDS));
    }

    @Test
    public void factoryRejectsNullGranularity() {
        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(URI, "db", "ts_", null));
    }

    @Test
    public void ofConfigAppliesDefaultsWhenOnlyUriIsPresent() {
        final Config config = ConfigFactory.parseString("uri = \"" + URI + "\"");

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(config);

        assertThat(underTest.getUri()).isEqualTo(URI);
        assertThat(underTest.getDatabase())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_DATABASE);
        assertThat(underTest.getCollectionPrefix())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_COLLECTION_PREFIX);
        assertThat(underTest.getGranularity())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_GRANULARITY);
    }

    @Test
    public void ofConfigReadsAllValuesWhenPresent() {
        final Map<String, Object> map = new HashMap<>();
        map.put("uri", URI);
        map.put("database", "my_ts");
        map.put("collection-prefix", "tsd_");
        map.put("granularity", "hours");
        final Config config = ConfigFactory.parseMap(map);

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(config);

        assertThat(underTest.getUri()).isEqualTo(URI);
        assertThat(underTest.getDatabase()).isEqualTo("my_ts");
        assertThat(underTest.getCollectionPrefix()).isEqualTo("tsd_");
        assertThat(underTest.getGranularity()).isEqualTo(Granularity.HOURS);
    }

    @Test
    public void ofConfigRejectsMissingUri() {
        final Config config = ConfigFactory.empty();

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(config))
                .withMessageContaining("uri");
    }

    @Test
    public void ofConfigRejectsUnknownGranularity() {
        final Config config = ConfigFactory.parseString(
                "uri = \"" + URI + "\", granularity = \"days\"");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(config))
                .withMessageContaining("days");
    }

    @Test
    public void ofConfigRejectsNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of((Config) null));
    }

    @Test
    public void toStringIncludesAllFields() {
        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(URI, "a", "b", Granularity.SECONDS);

        final String s = underTest.toString();

        assertThat(s).contains(URI).contains("a").contains("b").contains("seconds");
    }
}
