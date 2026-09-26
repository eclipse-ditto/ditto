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
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.timeseries.api.Capabilities;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
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
        assertThat(underTest.getMaxQueryResultSize())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_MAX_QUERY_RESULT_SIZE);
        assertThat(underTest.getQueryTimeout())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_QUERY_TIMEOUT);
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
        assertThat(underTest.getMaxQueryResultSize())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_MAX_QUERY_RESULT_SIZE);
        assertThat(underTest.getQueryTimeout())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_QUERY_TIMEOUT);
    }

    @Test
    public void ofConfigReadsAllValuesWhenPresent() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Map<String, Object> map = new HashMap<>();
        map.put("collection-prefix", "tsd_");
        map.put("granularity", "hours");
        map.put("retention", "30d");
        map.put("max-query-result-size", 500_000);
        map.put("query-timeout", "30s");
        final Config adapterConfig = ConfigFactory.parseMap(map);

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig);

        assertThat(underTest.getCollectionPrefix()).isEqualTo("tsd_");
        assertThat(underTest.getGranularity()).isEqualTo(Granularity.HOURS);
        assertThat(underTest.getRetention()).isPresent();
        assertThat(underTest.getMaxQueryResultSize()).isEqualTo(500_000);
        assertThat(underTest.getQueryTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    public void ofConfigDefaultsCapabilitiesWhenBlockAbsent() {
        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mock(MongoDbConfig.class), ConfigFactory.empty());

        assertThat(underTest.getCapabilities())
                .isEqualTo(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_CAPABILITIES);
    }

    @Test
    public void ofConfigReadsCapabilitiesBlock() {
        final Config adapterConfig = ConfigFactory.parseString(
                "capabilities { native-query = false, "
                        + "pushable-aggregations = [\"avg\", \"percentile\"], "
                        + "native-fill-strategies = [\"linear\"] }");

        final Capabilities caps =
                DefaultMongoDbTimeseriesAdapterConfig.of(mock(MongoDbConfig.class), adapterConfig)
                        .getCapabilities();

        assertThat(caps.supportsNativeQuery()).isFalse();
        assertThat(caps.canPushDown(Aggregation.PERCENTILE)).isTrue();
        assertThat(caps.canPushDown(Aggregation.AVG)).isTrue();
        assertThat(caps.canPushDown(Aggregation.SUM)).isFalse(); // replaced the default set
        assertThat(caps.canFillNatively(FillStrategy.LINEAR)).isTrue();
    }

    @Test
    public void ofConfigRejectsUnknownPushableAggregation() {
        final Config adapterConfig = ConfigFactory.parseString(
                "capabilities { pushable-aggregations = [\"avg\", \"bogus\"] }");

        assertThatExceptionOfType(DittoConfigError.class).isThrownBy(() ->
                DefaultMongoDbTimeseriesAdapterConfig.of(mock(MongoDbConfig.class), adapterConfig));
    }

    @Test
    public void ofConfigRejectsNonPositiveMaxQueryResultSize() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.parseString("max-query-result-size = 0");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig))
                .withMessageContaining("max-query-result-size");
    }

    @Test
    public void ofConfigRejectsNonPositiveQueryTimeout() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.parseString("query-timeout = \"0s\"");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig))
                .withMessageContaining("query-timeout");
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
    public void ofConfigTreatsUnlimitedRetentionAsNoExpiration() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.parseString("retention = \"unlimited\"");

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig);

        assertThat(underTest.getRetention()).isEmpty();
    }

    @Test
    public void ofConfigRejectsNonPositiveRetention() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.parseString("retention = \"0s\"");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig))
                .withMessageContaining("retention");
    }

    @Test
    public void ofConfigReadsRetentionOverridesAndResolvesPerNamespace() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig = ConfigFactory.parseString(
                "retention = 90d\n" +
                        "retention-overrides { \"com.acme.hifreq\" = 7d, \"org.eclipse.ditto\" = 365d }");

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig);

        assertThat(underTest.getRetentionOverrides())
                .containsOnly(entry("com.acme.hifreq", Duration.ofDays(7)),
                        entry("org.eclipse.ditto", Duration.ofDays(365)));
        // Override wins for a listed namespace; others fall back to the default.
        assertThat(underTest.getRetention("com.acme.hifreq")).contains(Duration.ofDays(7));
        assertThat(underTest.getRetention("org.eclipse.ditto")).contains(Duration.ofDays(365));
        assertThat(underTest.getRetention("other.namespace")).contains(Duration.ofDays(90));
    }

    @Test
    public void getRetentionForNamespaceIsEmptyWhenNeitherOverrideNorDefaultSet() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);

        final DefaultMongoDbTimeseriesAdapterConfig underTest =
                DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, ConfigFactory.empty());

        assertThat(underTest.getRetentionOverrides()).isEmpty();
        assertThat(underTest.getRetention("any.namespace")).isEmpty();
    }

    @Test
    public void ofConfigRejectsNonPositiveRetentionOverride() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig =
                ConfigFactory.parseString("retention-overrides { \"com.acme\" = \"0s\" }");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig))
                .withMessageContaining("com.acme");
    }

    @Test
    public void ofConfigRejectsPerNamespaceUnlimitedOverride() {
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        final Config adapterConfig =
                ConfigFactory.parseString("retention-overrides { \"com.acme\" = \"unlimited\" }");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, adapterConfig))
                .withMessageContaining("unlimited");
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
