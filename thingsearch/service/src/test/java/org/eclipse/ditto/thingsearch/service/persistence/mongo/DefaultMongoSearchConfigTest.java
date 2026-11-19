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
package org.eclipse.ditto.thingsearch.service.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.api.config.ReadPreference;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.thingsearch.persistence.api.config.SearchPersistenceConfig;
import org.junit.Rule;
import org.junit.Test;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultMongoSearchConfig}.
 */
public final class DefaultMongoSearchConfigTest {

    private static final Config RESOURCE =
            ConfigFactory.parseResourcesAnySyntax("mongo-search-config-test").resolve();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private static Config withOverrides(final String searchBlock) {
        return ConfigFactory.parseString("ditto.search {\n" + searchBlock + "\n}").withFallback(RESOURCE).resolve();
    }

    private static Index indexNamed(final MongoSearchConfig config, final String name) {
        return config.getCustomIndexesAsIndices().stream()
                .filter(index -> index.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMongoSearchConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void defaultsMatchTodaysSearchConf() {
        final MongoSearchConfig underTest = MongoSearchConfig.of(RESOURCE);

        softly.assertThat(underTest.getMongoHintsByNamespace()).as("hints").isEmpty();
        softly.assertThat(underTest.getMongoCountHintIndexName()).as("count hint").isEmpty();
        softly.assertThat(underTest.getSimpleFieldMappings()).as("simple-field-mappings").isEqualTo(Map.of(
                "thingId", "_id",
                "namespace", "_namespace",
                "policyId", "/policyId",
                "_revision", "/_revision",
                "_modified", "/_modified",
                "_created", "/_created",
                "definition", "/definition",
                "_metadata", "/_metadata"));
        softly.assertThat(underTest.getCustomIndexesAsIndices()).as("custom indexes").isEmpty();
        softly.assertThat(underTest.getIndexInitializationConfig().isIndexInitializationConfigEnabled())
                .as("index-initialization.enabled")
                .isTrue();
        softly.assertThat(underTest.getIndexInitializationConfig().getActivatedIndexNames())
                .as("index-initialization.activated-index-names")
                .isEqualTo(Set.of("_namespace", "global_read", "v_wildcard", "policyId", "referencedPolicies",
                        "deleteAt"));
        softly.assertThat(underTest.getQueryPersistenceConfig().readPreference()).as("query readPreference")
                .isEqualTo(ReadPreference.PRIMARY);
        softly.assertThat(underTest.getQueryPersistenceConfig().readConcern()).as("query readConcern")
                .isEqualTo(ReadConcern.LOCAL);
        softly.assertThat(underTest.getUpdaterPersistenceConfig().readPreference()).as("updater readPreference")
                .isEqualTo(ReadPreference.PRIMARY);
        softly.assertThat(underTest.getUpdaterPersistenceConfig().readConcern()).as("updater readConcern")
                .isEqualTo(ReadConcern.LOCAL);
        softly.assertThat(underTest.getUpdaterStreamPersistenceConfig().getParallelism()).as("stream parallelism")
                .isEqualTo(2);
        softly.assertThat(underTest.getUpdaterStreamPersistenceConfig().getAckDelay()).as("stream ack-delay")
                .isEqualTo(Duration.ZERO);
        softly.assertThat(underTest.getWithAcknowledgementsWriteConcern()).as("with-acks-writeConcern")
                .isEqualTo(WriteConcern.JOURNALED);
        softly.assertThat(underTest.getCustomAggregationMetricsPersistenceConfig())
                .as("custom-aggregation-metrics-persistence")
                .isEmpty();
        softly.assertThat(underTest.getMongoDbConfig().getMongoDbUri()).as("mongodb uri")
                .startsWith("mongodb://localhost:27017/test");
        softly.assertThat(underTest.getMongoDbConfig()).as("mongodb config")
                .isEqualTo(DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(RESOURCE)));
    }

    @Test
    public void overriddenValuesAreRead() {
        final Config config = ConfigFactory.parseString("ditto.mongodb.uri = \"mongodb://other-host:27018/search\"")
                .withFallback(withOverrides("""
                        mongo-hints-by-namespace = "{\\"org.eclipse.ditto\\": \\"v_wildcard\\"}"
                        mongo-count-hint-index-name = "_namespace"
                        simple-field-mappings { thingId = "/thingId" }
                        index-initialization {
                          enabled = false
                          activated-index-names = ["policyId"]
                          custom-indexes {
                            zz_second_by_name {
                              fields = [
                                { name = "t.attributes.region", direction = "ASC" }
                                { name = "t.attributes.timestamp", direction = "DESC" }
                              ]
                            }
                            aa_first_by_name {
                              fields = [ { name = "_namespace", direction = "DESC" } ]
                            }
                          }
                        }
                        query.persistence { readPreference = nearest, readConcern = majority }
                        updater.persistence { readPreference = secondary, readConcern = available }
                        updater.stream.persistence {
                          parallelism = 7
                          ack-delay = 3s
                          with-acks-writeConcern = majority
                        }
                        operator-metrics.custom-aggregation-metrics-persistence {
                          readPreference = secondaryPreferred
                          readConcern = linearizable
                        }
                        """));
        final MongoSearchConfig underTest = MongoSearchConfig.of(config);

        softly.assertThat(underTest.getMongoHintsByNamespace()).as("hints")
                .contains("{\"org.eclipse.ditto\": \"v_wildcard\"}");
        softly.assertThat(underTest.getMongoCountHintIndexName()).as("count hint").contains("_namespace");
        softly.assertThat(underTest.getSimpleFieldMappings()).as("simple-field-mappings")
                .containsEntry("thingId", "/thingId")
                .containsEntry("namespace", "_namespace");
        softly.assertThat(underTest.getIndexInitializationConfig().isIndexInitializationConfigEnabled())
                .as("index-initialization.enabled")
                .isFalse();
        softly.assertThat(underTest.getIndexInitializationConfig().getActivatedIndexNames())
                .as("index-initialization.activated-index-names")
                .containsExactly("policyId");

        // the entry order of the custom-indexes ConfigObject (typesafe config's entrySet() does not keep the source
        // order):
        softly.assertThat(underTest.getCustomIndexesAsIndices()).as("custom index names in config object order")
                .extracting(Index::getName)
                .containsExactlyElementsOf(config.getObject("ditto.search.index-initialization.custom-indexes")
                        .entrySet().stream().map(Map.Entry::getKey).toList());
        final Index first = indexNamed(underTest, "zz_second_by_name");
        softly.assertThat(first.getKeys().keySet()).as("first index key order")
                .containsExactly("t.attributes.region", "t.attributes.timestamp");
        softly.assertThat(first.getKeys().getInt32("t.attributes.region").getValue()).as("ASC").isEqualTo(1);
        softly.assertThat(first.getKeys().getInt32("t.attributes.timestamp").getValue()).as("DESC").isEqualTo(-1);
        final Index second = indexNamed(underTest, "aa_first_by_name");
        softly.assertThat(second.getKeys().getInt32("_namespace").getValue()).as("DESC").isEqualTo(-1);
        softly.assertThat(first.isUnique()).as("custom index is not unique").isFalse();

        softly.assertThat(underTest.getQueryPersistenceConfig().readPreference()).as("query readPreference")
                .isEqualTo(ReadPreference.NEAREST);
        softly.assertThat(underTest.getQueryPersistenceConfig().readConcern()).as("query readConcern")
                .isEqualTo(ReadConcern.MAJORITY);
        softly.assertThat(underTest.getUpdaterPersistenceConfig().readPreference()).as("updater readPreference")
                .isEqualTo(ReadPreference.SECONDARY);
        softly.assertThat(underTest.getUpdaterPersistenceConfig().readConcern()).as("updater readConcern")
                .isEqualTo(ReadConcern.AVAILABLE);
        softly.assertThat(underTest.getUpdaterStreamPersistenceConfig().getParallelism()).as("stream parallelism")
                .isEqualTo(7);
        softly.assertThat(underTest.getUpdaterStreamPersistenceConfig().getAckDelay()).as("stream ack-delay")
                .isEqualTo(Duration.ofSeconds(3));
        softly.assertThat(underTest.getWithAcknowledgementsWriteConcern()).as("with-acks-writeConcern")
                .isEqualTo(WriteConcern.MAJORITY);
        softly.assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()
                        .map(SearchPersistenceConfig::readPreference))
                .as("custom-aggregation readPreference")
                .contains(ReadPreference.SECONDARY_PREFERRED);
        softly.assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()
                        .map(SearchPersistenceConfig::readConcern))
                .as("custom-aggregation readConcern")
                .contains(ReadConcern.LINEARIZABLE);
        softly.assertThat(underTest.getMongoDbConfig().getMongoDbUri()).as("mongodb uri")
                .startsWith("mongodb://other-host:27018/search");
    }

    @Test
    public void customAggregationBlockInheritsMissingReadSettingFromQuery() {
        final MongoSearchConfig underTest = MongoSearchConfig.of(withOverrides("""
                query.persistence { readPreference = nearest, readConcern = linearizable }
                operator-metrics.custom-aggregation-metrics-persistence { readPreference = secondary }
                """));

        softly.assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()
                        .map(SearchPersistenceConfig::readPreference))
                .as("readPreference named by the block")
                .contains(ReadPreference.SECONDARY);
        softly.assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()
                        .map(SearchPersistenceConfig::readConcern))
                .as("readConcern inherited from query.persistence")
                .contains(ReadConcern.LINEARIZABLE);
    }

    @Test
    public void customAggregationBlockAbsentIsEmpty() {
        final MongoSearchConfig underTest = MongoSearchConfig.of(withOverrides("""
                operator-metrics.custom-metrics-persistence { readPreference = secondary }
                """));

        softly.assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()).isEmpty();
    }

    @Test
    public void unknownWriteConcernIsRefusedWithConfigError() {
        assertThatCode(() -> MongoSearchConfig.of(
                withOverrides("updater.stream.persistence.with-acks-writeConcern = majority")))
                .as("control: a known write concern builds")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> MongoSearchConfig.of(
                withOverrides("updater.stream.persistence.with-acks-writeConcern = bogus")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("<bogus>");
    }

    @Test
    public void invalidReadPreferenceIsRefused() {
        assertThatThrownBy(() -> MongoSearchConfig.of(withOverrides("query.persistence.readPreference = bogus")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("<bogus>");
    }

}
