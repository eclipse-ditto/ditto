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
import javax.annotation.concurrent.Immutable;

import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.Document;

import com.typesafe.config.Config;

import akka.contrib.persistence.mongodb.JournallingFieldNames$;

/**
 * Provides {@link MongoOpsSelection}s for selecting/deleting documents in a MongoDB EventSource persistence.
 */
@Immutable
public final class MongoOpsSelectionProvider {

    private static final String PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    private final MongoEventSourceSettings settings;

    private MongoOpsSelectionProvider(final MongoEventSourceSettings settings) {
        this.settings = requireNonNull(settings);
    }

    /**
     * Create a new instance.
     *
     * @param settings the MongoDB EventSource settings
     *
     * @return the instance
     */
    public static MongoOpsSelectionProvider of(final MongoEventSourceSettings settings) {
        return new MongoOpsSelectionProvider(settings);
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
        return of(MongoEventSourceSettings.of(persistenceIdPrefix, supportsNamespaces,
                metadataCollectionName, journalCollectionName, snapshotCollectionName, suffixSeparator));
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
        return of(MongoEventSourceSettings.fromConfig(config, persistenceIdPrefix, supportsNamespaces,
                journalPluginId, snapshotPluginId));
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

        if (settings.isSupportsNamespaces()) {
            validateAndExtractNamespace(entityId);
        }

        return settings.getSuffixSeparator().isPresent()
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

        if (!settings.isSupportsNamespaces()) {
            throw new UnsupportedOperationException("Namespaces are not supported");
        }

        return settings.getSuffixSeparator().isPresent()
                ? selectNamespaceWithSuffix(namespace)
                : selectNamespaceWithoutSuffix(namespace);
    }

    private Collection<MongoOpsSelection> selectEntityBySuffix(final String entityId) {
        return Arrays.asList(
                selectEntityByPid(settings.getMetadataCollectionName(), entityId), // collection "*Metadata" has no namespace suffix
                selectEntityBySuffix(settings.getJournalCollectionName(), entityId),
                selectEntityBySuffix(settings.getSnapshotCollectionName(), entityId));
    }

    private Collection<MongoOpsSelection> selectEntityWithoutSuffix(final String entityId) {
        return Arrays.asList(
                selectEntityByPid(settings.getMetadataCollectionName(), entityId),
                selectEntityByPid(settings.getJournalCollectionName(), entityId),
                selectEntityByPid(settings.getSnapshotCollectionName(), entityId));
    }

    private Collection<MongoOpsSelection> selectNamespaceWithSuffix(final String namespace) {
        return Arrays.asList(
                selectNamespaceByPid(settings.getMetadataCollectionName(), namespace), // collection "*Metadata" has no namespace suffix
                selectNamespaceBySuffix(settings.getJournalCollectionName(), namespace),
                selectNamespaceBySuffix(settings.getSnapshotCollectionName(), namespace));
    }

    private Collection<MongoOpsSelection> selectNamespaceWithoutSuffix(final String namespace) {
        return Arrays.asList(
                selectNamespaceByPid(settings.getMetadataCollectionName(), namespace),
                selectNamespaceByPid(settings.getJournalCollectionName(), namespace),
                selectNamespaceByPid(settings.getSnapshotCollectionName(), namespace));
    }

    private MongoOpsSelection selectNamespaceBySuffix(final String collection, final String namespace) {
        final String suffixSeparator = settings.getSuffixSeparator().orElseThrow(IllegalStateException::new);
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        return MongoOpsSelection.of(suffixedCollection, new Document());
    }

    private MongoOpsSelection selectNamespaceByPid(final String collection, final String namespace) {
        return MongoOpsSelection.of(collection, filterByPidPrefix(namespace));
    }

    private Document filterByPidPrefix(final String namespace) {
        final String pidRegex = String.format("^%s%s:", settings.getPersistenceIdPrefix(), namespace);
        return new Document(PID, new BsonRegularExpression(pidRegex));
    }

    private MongoOpsSelection selectEntityBySuffix(final String collection, final String entityId) {
        final String namespace = validateAndExtractNamespace(entityId);
        final String suffixSeparator = settings.getSuffixSeparator().orElseThrow(IllegalStateException::new);
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        final Document filter = filterByPid(entityId);
        return MongoOpsSelection.of(suffixedCollection, filter);
    }

    private String validateAndExtractNamespace(final String entityId) {
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
        final String pid = String.format("%s%s", settings.getPersistenceIdPrefix(), entityId);
        return new Document(PID, new BsonString(pid));
    }

}
