/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.Document;

import com.typesafe.config.Config;

import akka.contrib.persistence.mongodb.JournallingFieldNames$;

/**
 * Provides {@link MongoOpsSelection}s for selecting/deleting documents in a MongoDB EventSource persistence.
 */
public class MongoOpsSelectionProvider {

    private static final String PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    private final String persistenceIdPrefix;

    // realtime journal is not touched because it is a capped collection
    private final String metadataCollectionName;
    private final String journalCollectionName;
    private final String snapshotCollectionName;
    private final boolean supportsNamespaces;
    @Nullable
    private final String suffixSeparator;

    private MongoOpsSelectionProvider(final String persistenceIdPrefix,
            final boolean supportsNamespaces, final String metadataCollectionName, final String journalCollectionName,
            final String snapshotCollectionName, @Nullable final String suffixSeparator) {
        this.persistenceIdPrefix = requireNonNull(persistenceIdPrefix);
        this.supportsNamespaces = supportsNamespaces;
        this.metadataCollectionName = requireNonNull(metadataCollectionName);
        this.journalCollectionName = requireNonNull(journalCollectionName);
        this.snapshotCollectionName = requireNonNull(snapshotCollectionName);
        this.suffixSeparator = suffixSeparator;
    }

    /**
     * Create a new instance.
     *
     * @param persistenceIdPrefix the prefix of the persistence id
     * @param supportsNamespaces whether the underlying EventSource supports namespaces
     * @param metadataCollectionName the name of the metadata collection
     * @param journalCollectionName the name of the journal collection
     * @param snapshotCollectionName the name of the snapshot collection
     * @param suffixSeparator the suffix separator, may be {@code null}: if not null, it is assumed that there is one
     * collection per namespace with the córresponding suffix
     *
     * @return the instance
     */
    public static MongoOpsSelectionProvider of(final String persistenceIdPrefix,
            final boolean supportsNamespaces, final String metadataCollectionName, final String journalCollectionName,
            final String snapshotCollectionName, @Nullable final String suffixSeparator) {
        return new MongoOpsSelectionProvider(persistenceIdPrefix, supportsNamespaces,
                metadataCollectionName, journalCollectionName, snapshotCollectionName, suffixSeparator);
    }

    /**
     * Create a new instance.
     *
     * @param persistenceIdPrefix the prefix of the persistence id
     * @param supportsNamespaces whether the underlying EventSource supports namespaces
     * @param config the config which contains the configuration of the EventSource
     * @param journalPluginId the ID of the journal plugin to be read from the {@code config}
     * @param snapshotPluginId the ID of the snapshot plugin to be read from the {@code config}
     *
     * @return the instance
     */
    public static MongoOpsSelectionProvider of(final String persistenceIdPrefix,
            final boolean supportsNamespaces, final Config config,
            final String journalPluginId,
            final String snapshotPluginId) {
        requireNonNull(persistenceIdPrefix);
        requireNonNull(config);
        requireNonNull(journalPluginId);
        requireNonNull(snapshotPluginId);

        final String metadataCollectionName = getCollectionName(config, journalPluginId, "metadata");
        final String journalCollectionName = getCollectionName(config, journalPluginId, "journal");
        final String snapshotCollectionName = getCollectionName(config, snapshotPluginId, "snaps");

        final String suffixSeparator;
        if (supportsNamespaces) {
            final boolean isSuffixBuilderEnabled =
                    !readConfig(config, suffixBuilderPath("class"), "").trim().isEmpty();
            if (isSuffixBuilderEnabled) {
                suffixSeparator = readConfig(config, suffixBuilderPath("separator"), "@");
            } else {
                suffixSeparator = null;
            }
        } else {
            suffixSeparator = null;
        }

        return new MongoOpsSelectionProvider(persistenceIdPrefix, supportsNamespaces,
                metadataCollectionName, journalCollectionName, snapshotCollectionName, suffixSeparator);
    }

    /**
     * Select an entity by its ID.
     * @param entityId the ID
     *
     * @return a collection of {@link MongoOpsSelection} which represent all occurrence of the entity in the
     * EventSource.
     */
    public Collection<MongoOpsSelection> selectEntity(final String entityId) {
        requireNonNull(entityId);

        return suffixSeparator != null
                ? selectEntityBySuffix(entityId)
                : selectEntityWithoutSuffix(entityId);
    }

    /**
     * Select a complete namespace.
     * @param namespace the namespace
     *
     * @return a collection of {@link MongoOpsSelection} which represent all occurrence of a namespace in the
     * EventSource.
     */
    public Collection<MongoOpsSelection> selectNamespace(final String namespace) {
        requireNonNull(namespace);

        if (!supportsNamespaces) {
            throw new UnsupportedOperationException("Namespaces are not supported");
        }

        return suffixSeparator != null
                ? selectNamespaceWithSuffix(namespace)
                : selectNamespaceWithoutSuffix(namespace);
    }

    private Collection<MongoOpsSelection> selectEntityBySuffix(final String entityId) {
        return Arrays.asList(
                selectEntityByPid(metadataCollectionName, entityId), // collection "*Metadata" has no namespace suffix
                selectEntityBySuffix(journalCollectionName, entityId),
                selectEntityBySuffix(snapshotCollectionName, entityId));
    }

    private Collection<MongoOpsSelection> selectEntityWithoutSuffix(final String entityId) {
        return Arrays.asList(
                selectEntityByPid(metadataCollectionName, entityId),
                selectEntityByPid(journalCollectionName, entityId),
                selectEntityByPid(snapshotCollectionName, entityId));
    }

    private Collection<MongoOpsSelection> selectNamespaceWithSuffix(final String namespace) {
        return Arrays.asList(
                selectNamespaceByPid(metadataCollectionName, namespace), // collection "*Metadata" has no namespace suffix
                selectNamespaceBySuffix(journalCollectionName, namespace),
                selectNamespaceBySuffix(snapshotCollectionName, namespace));
    }

    private Collection<MongoOpsSelection> selectNamespaceWithoutSuffix(final String namespace) {
        return Arrays.asList(
                selectNamespaceByPid(metadataCollectionName, namespace),
                selectNamespaceByPid(journalCollectionName, namespace),
                selectNamespaceByPid(snapshotCollectionName, namespace));
    }

    private MongoOpsSelection selectNamespaceBySuffix(final String collection, final String namespace) {
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        return MongoOpsSelection.of(suffixedCollection, new Document());
    }

    private MongoOpsSelection selectNamespaceByPid(final String collection, final String namespace) {
        return MongoOpsSelection.of(collection, filterByPidPrefix(namespace));
    }

    private Document filterByPidPrefix(final String namespace) {
        final String pidRegex = String.format("^%s%s:", persistenceIdPrefix, namespace);
        return new Document(PID, new BsonRegularExpression(pidRegex));
    }

    private MongoOpsSelection selectEntityBySuffix(final String collection, final String entityId) {
        final String namespace = extractNamespace(entityId);
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        final Document filter = filterByPid(entityId);
        return MongoOpsSelection.of(suffixedCollection, filter);
    }

    private String extractNamespace(final String entityId) {
        final int separatorIndex = entityId.indexOf(':');
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("entityId does not have namespace: " + entityId);
        }

        return entityId.substring(0, separatorIndex);
    }

    private MongoOpsSelection selectEntityByPid(final String collection, final String entityId) {
        return MongoOpsSelection.of(collection, filterByPid(entityId));
    }

    private Document filterByPid(final String entityId) {
        final String pid = String.format("%s%s", persistenceIdPrefix, entityId);
        return new Document(PID, new BsonString(pid));
    }

    private static String getCollectionName(final Config config, final String root, final String collectionType) {
        return config.getString(String.format("%s.overrides.%s-collection", root, collectionType));
    }

    private static String readConfig(final Config config, final String path, final String fallback) {
        return config.hasPath(path) ? config.getString(path) : fallback;
    }

    private static String suffixBuilderPath(final String key) {
        return "akka.contrib.persistence.mongodb.mongo.suffix-builder." + key;
    }
}
