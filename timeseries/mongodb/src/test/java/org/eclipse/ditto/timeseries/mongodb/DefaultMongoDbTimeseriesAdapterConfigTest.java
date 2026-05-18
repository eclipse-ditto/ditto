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
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultMongoDbTimeseriesAdapterConfig}.
 */
public final class DefaultMongoDbTimeseriesAdapterConfigTest {

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(DefaultMongoDbTimeseriesAdapterConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithGivenValues() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, "x_", Granularity.MINUTES);

        assertThat(underTest.getMongoDbConfig()).isSameAs(mongoDbConfig);
        assertThat(underTest.getCollectionPrefix()).isEqualTo("x_");
        assertThat(underTest.getGranularity()).isEqualTo(Granularity.MINUTES);
        assertThat(underTest.getRetention()).isEmpty();
    }

    @Test
    public void factoryRejectsNullMongoDbConfig() {
        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(null, "ts_", Granularity.SECONDS));
    }

    @Test
    public void factoryRejectsNullCollectionPrefix() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);

        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, null, Granularity.SECONDS));
    }

    @Test
    public void factoryRejectsNullGranularity() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);

        assertThatNullPointerException().isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, "ts_", null));
    }

    @Test
    public void ofConfigAppliesDefaultsWhenAdapterConfigIsEmpty() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.empty();

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig);

        assertThat(underTest.getMongoDbConfig()).isSameAs(mongoDbConfig);
        assertThat(underTest.getCollectionPrefix())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_COLLECTION_PREFIX);
        assertThat(underTest.getGranularity())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_GRANULARITY);
        assertThat(underTest.getRetention()).isEmpty();
    }

    @Test
    public void ofConfigReadsAllValuesWhenPresent() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Map<String, Object> map = new HashMap<>();
        map.put("collection-prefix", "tsd_");
        map.put("granularity", "hours");
        map.put("retention", "30d");
        final Config adapterConfig = ConfigFactory.parseMap(map);

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig);

        assertThat(underTest.getCollectionPrefix()).isEqualTo("tsd_");
        assertThat(underTest.getGranularity()).isEqualTo(Granularity.HOURS);
        assertThat(underTest.getRetention()).isPresent();
    }

    @Test
    public void ofConfigRejectsUnknownGranularity() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.parseString("granularity = \"days\"");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig))
                .withMessageContaining("days");
    }

    @Test
    public void ofConfigRejectsNullMongoDbConfig() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(null, ConfigFactory.empty()));
    }

    @Test
    public void ofConfigRejectsNullAdapterConfig() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);

        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, (Config) null));
    }

    @Test
    public void toStringIncludesAllFields() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, "b", Granularity.SECONDS);

        final String s = underTest.toString();

        assertThat(s).contains("collectionPrefix=b").contains("granularity=seconds");
    }
}
