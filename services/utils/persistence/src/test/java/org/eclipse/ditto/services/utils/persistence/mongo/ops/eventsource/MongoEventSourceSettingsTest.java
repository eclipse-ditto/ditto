/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link MongoEventSourceSettings}.
 */
public class MongoEventSourceSettingsTest {

    private static final String DEFAULT_SUFFIX_SEPARATOR = "@";

    private static final String PERSISTENCE_ID_PREFIX = "myPidPrefix:";
    private static final boolean SUPPORTS_NAMESPACES = true;
    private static final String METADATA_COLLECTION_NAME = "myMetadataCollection";
    private static final String JOURNAL_COLLECTION_NAME = "myJournalCollection";
    private static final String SNAPSHOT_COLLECTION_NAME = "mySnapshotCollection";
    private static final String SUFFIX_SEPARATOR = "@@@";

    private static final String JOURNAL_PLUGIN_ID = "my-journal-plugin-id";
    private static final String SNAPSHOT_PLUGIN_ID = "my-snapshot-plugin-id";

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoEventSourceSettings.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MongoEventSourceSettings.class).verify();
    }

    @Test
    public void createAndCheckGetters() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                SUFFIX_SEPARATOR);

        assertThat(settings).isNotNull();
        assertThat(settings.getPersistenceIdPrefix()).isEqualTo(PERSISTENCE_ID_PREFIX);
        assertThat(settings.isSupportsNamespaces()).isEqualTo(SUPPORTS_NAMESPACES);
        assertThat(settings.getMetadataCollectionName()).isEqualTo(METADATA_COLLECTION_NAME);
        assertThat(settings.getJournalCollectionName()).isEqualTo(JOURNAL_COLLECTION_NAME);
        assertThat(settings.getSnapshotCollectionName()).isEqualTo(SNAPSHOT_COLLECTION_NAME);
        assertThat(settings.getSuffixSeparator()).hasValue(SUFFIX_SEPARATOR);
    }

    @Test
    public void createWithNullSuffixSeparator() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                null);

        assertThat(settings).isNotNull();
        assertThat(settings.getSuffixSeparator()).isEmpty();
    }

    @Test
    public void createWithSuffixSeparatorAndNotSupportsNamespacesFails() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                        false, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                        SUFFIX_SEPARATOR));
    }

    @Test
    public void fromConfigWithSuffixBuilderClassAndNullSeparator() {
        final Config config = createConfig("foo", null);

        final MongoEventSourceSettings actual =
                MongoEventSourceSettings.fromConfig(config, PERSISTENCE_ID_PREFIX,
                        SUPPORTS_NAMESPACES, JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID);

        final MongoEventSourceSettings expected = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                DEFAULT_SUFFIX_SEPARATOR);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromConfigWithSuffixBuilderClassAndNotSupportsNamespacesFails() {
        final Config config = createConfig("foo", null);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                MongoEventSourceSettings.fromConfig(config, PERSISTENCE_ID_PREFIX,
                        false, JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID));
    }

    @Test
    public void fromConfigWithSuffixBuilderClassAndCustomSeparator() {
        final Config config = createConfig("foo", SUFFIX_SEPARATOR);

        final MongoEventSourceSettings actual =
                MongoEventSourceSettings.fromConfig(config, PERSISTENCE_ID_PREFIX,
                        SUPPORTS_NAMESPACES, JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID);

        final MongoEventSourceSettings expected = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                SUFFIX_SEPARATOR);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromConfigWithNullSuffixBuilderClass() {
        final Config config = createConfig(null, "mustBeIgnored");

        final MongoEventSourceSettings actual =
                MongoEventSourceSettings.fromConfig(config, PERSISTENCE_ID_PREFIX,
                        SUPPORTS_NAMESPACES, JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID);

        final MongoEventSourceSettings expected = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromConfigWithEmptySuffixBuilderClass() {
        final Config config = createConfig("   ", "mustBeIgnored");

        final MongoEventSourceSettings actual =
                MongoEventSourceSettings.fromConfig(config, PERSISTENCE_ID_PREFIX,
                        SUPPORTS_NAMESPACES, JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID);

        final MongoEventSourceSettings expected = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME,
                null);
        assertThat(actual).isEqualTo(expected);
    }


    @Test
    public void fromConfigFailsWithEmptyConfig() {
        assertThatExceptionOfType(ConfigException.Missing.class).isThrownBy(() ->
                MongoEventSourceSettings.fromConfig(ConfigFactory.empty(), PERSISTENCE_ID_PREFIX, SUPPORTS_NAMESPACES,
                        JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID));
    }

    private Config createConfig(@Nullable final String suffixBuilderClass,
            @Nullable final String suffixBuilderSeparator) {

        final Map<String, Object> configMap = new HashMap<>();

        configMap.put(JOURNAL_PLUGIN_ID + ".overrides.metadata-collection", METADATA_COLLECTION_NAME);
        configMap.put(JOURNAL_PLUGIN_ID + ".overrides.journal-collection", JOURNAL_COLLECTION_NAME);
        configMap.put(SNAPSHOT_PLUGIN_ID + ".overrides.snaps-collection", SNAPSHOT_COLLECTION_NAME);

        final String suffixBuilderPrefix = "akka.contrib.persistence.mongodb.mongo.suffix-builder.";

        if (suffixBuilderClass != null) {
            configMap.put(suffixBuilderPrefix + "class", suffixBuilderClass);
        }

        if (suffixBuilderSeparator != null) {
            configMap.put(suffixBuilderPrefix + "separator", suffixBuilderSeparator);
        }

        return ConfigFactory.parseMap(configMap);
    }
}
