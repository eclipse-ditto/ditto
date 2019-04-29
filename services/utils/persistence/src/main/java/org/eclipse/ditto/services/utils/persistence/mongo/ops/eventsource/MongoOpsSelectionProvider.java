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

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.Document;

import akka.contrib.persistence.mongodb.JournallingFieldNames$;

/**
 * Provides {@link MongoOpsSelection}s for selecting/deleting documents in a MongoDB EventSource persistence.
 */
@Immutable
final class MongoOpsSelectionProvider {

    private static final String PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    private final MongoEventSourceSettings settings;

    private MongoOpsSelectionProvider(final MongoEventSourceSettings settings) {
        this.settings = requireNonNull(settings);
    }

    /**
     * Create a new instance.
     *
     * @param settings the MongoDB EventSource settings
     * @return the instance
     */
    public static MongoOpsSelectionProvider of(final MongoEventSourceSettings settings) {
        return new MongoOpsSelectionProvider(settings);
    }

    /**
     * Select an entity by its ID.
     *
     * @param entityId the ID
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
     *
     * @param namespace the namespace
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
        return Collections.unmodifiableList(Arrays.asList(
                selectEntityByPid(settings.getMetadataCollectionName(), entityId),
                // collection "*Metadata" has no namespace suffix
                selectEntityBySuffix(settings.getJournalCollectionName(), entityId),
                selectEntityBySuffix(settings.getSnapshotCollectionName(), entityId)));
    }

    private Collection<MongoOpsSelection> selectEntityWithoutSuffix(final String entityId) {
        return Collections.unmodifiableList(Arrays.asList(
                selectEntityByPid(settings.getMetadataCollectionName(), entityId),
                selectEntityByPid(settings.getJournalCollectionName(), entityId),
                selectEntityByPid(settings.getSnapshotCollectionName(), entityId)));
    }

    private Collection<MongoOpsSelection> selectNamespaceWithSuffix(final String namespace) {
        return Collections.unmodifiableList(Arrays.asList(
                selectNamespaceByPid(settings.getMetadataCollectionName(), namespace),
                // collection "*Metadata" has no namespace suffix
                selectNamespaceBySuffix(settings.getJournalCollectionName(), namespace),
                selectNamespaceBySuffix(settings.getSnapshotCollectionName(), namespace)));
    }

    private Collection<MongoOpsSelection> selectNamespaceWithoutSuffix(final String namespace) {
        return Collections.unmodifiableList(Arrays.asList(
                selectNamespaceByPid(settings.getMetadataCollectionName(), namespace),
                selectNamespaceByPid(settings.getJournalCollectionName(), namespace),
                selectNamespaceByPid(settings.getSnapshotCollectionName(), namespace)));
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
