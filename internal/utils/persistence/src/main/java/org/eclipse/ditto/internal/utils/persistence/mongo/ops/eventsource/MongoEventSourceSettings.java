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
package org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;

/**
 * Setting of a MongoDB EventSource persistence.
 */
@Immutable
public final class MongoEventSourceSettings {

    private final String persistenceIdPrefix;

    // realtime journal is not touched because it is a capped collection
    private final String metadataCollectionName;
    private final String journalCollectionName;
    private final String snapshotCollectionName;
    private final boolean supportsNamespaces;

    private MongoEventSourceSettings(final String persistenceIdPrefix,
            final boolean supportsNamespaces,
            final String metadataCollectionName,
            final String journalCollectionName,
            final String snapshotCollectionName) {

        this.persistenceIdPrefix = checkNotNull(persistenceIdPrefix, "persistence ID prefix");
        this.supportsNamespaces = supportsNamespaces;
        this.metadataCollectionName = checkNotNull(metadataCollectionName, "metadata collection name");
        this.journalCollectionName = checkNotNull(journalCollectionName, "journal collection name");
        this.snapshotCollectionName = checkNotNull(snapshotCollectionName, "snapshot collection name");
    }

    /**
     * Create a new instance.
     *
     * @param persistenceIdPrefix the prefix of the persistence ID.
     * @param supportsNamespaces whether the underlying EventSource supports namespaces.
     * @param metadataCollectionName the name of the metadata collection.
     * @param journalCollectionName the name of the journal collection.
     * @param snapshotCollectionName the name of the snapshot collection.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MongoEventSourceSettings of(final String persistenceIdPrefix,
            final boolean supportsNamespaces,
            final String metadataCollectionName,
            final String journalCollectionName,
            final String snapshotCollectionName) {

        return new MongoEventSourceSettings(persistenceIdPrefix, supportsNamespaces,
                metadataCollectionName, journalCollectionName, snapshotCollectionName);
    }

    /**
     * Create a new instance based on a {@link Config}.
     *
     * @param config the config which contains the configuration of the EventSource
     * @param persistenceIdPrefix the prefix of the persistence ID.
     * @param supportsNamespaces whether the underlying EventSource supports namespaces
     * @param journalPluginId the ID of the journal plugin to be read from the {@code config}
     * @param snapshotPluginId the ID of the snapshot plugin to be read from the {@code config}
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MongoEventSourceSettings fromConfig(final Config config,
            final String persistenceIdPrefix,
            final boolean supportsNamespaces,
            final String journalPluginId,
            final String snapshotPluginId) {

        checkNotNull(config, "config");
        checkNotNull(journalPluginId, "journal plugin ID");
        checkNotNull(snapshotPluginId, "snapshot plugin ID");

        final String metadataCollectionName = getCollectionName(config, journalPluginId, "metadata");
        final String journalCollectionName = getCollectionName(config, journalPluginId, "journal");
        final String snapshotCollectionName = getCollectionName(config, snapshotPluginId, "snaps");

        return new MongoEventSourceSettings(persistenceIdPrefix, supportsNamespaces, metadataCollectionName,
                journalCollectionName, snapshotCollectionName);
    }

    /**
     * @return the prefix of the persistence id
     */
    public String getPersistenceIdPrefix() {
        return persistenceIdPrefix;
    }

    /**
     * @return the name of the metadata collection
     */
    public String getMetadataCollectionName() {
        return metadataCollectionName;
    }

    /**
     * @return the name of the journal collection
     */
    public String getJournalCollectionName() {
        return journalCollectionName;
    }

    /**
     * @return the name of the snapshot collection
     */
    public String getSnapshotCollectionName() {
        return snapshotCollectionName;
    }

    /**
     * @return whether the underlying EventSource supports namespaces
     */
    public boolean isSupportsNamespaces() {
        return supportsNamespaces;
    }

    private static String getCollectionName(final Config config, final String root, final String collectionType) {
        return config.getString(String.format("%s.overrides.%s-collection", root, collectionType));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MongoEventSourceSettings that = (MongoEventSourceSettings) o;
        return supportsNamespaces == that.supportsNamespaces &&
                Objects.equals(persistenceIdPrefix, that.persistenceIdPrefix) &&
                Objects.equals(metadataCollectionName, that.metadataCollectionName) &&
                Objects.equals(journalCollectionName, that.journalCollectionName) &&
                Objects.equals(snapshotCollectionName, that.snapshotCollectionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persistenceIdPrefix, metadataCollectionName, journalCollectionName, snapshotCollectionName,
                supportsNamespaces);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "persistenceIdPrefix='" + persistenceIdPrefix + '\'' +
                ", metadataCollectionName='" + metadataCollectionName + '\'' +
                ", journalCollectionName='" + journalCollectionName + '\'' +
                ", snapshotCollectionName='" + snapshotCollectionName + '\'' +
                ", supportsNamespaces=" + supportsNamespaces +
                ']';
    }

}
