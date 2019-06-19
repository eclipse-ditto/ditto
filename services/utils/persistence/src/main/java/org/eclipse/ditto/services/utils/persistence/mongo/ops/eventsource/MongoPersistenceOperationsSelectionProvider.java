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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.Document;

import akka.contrib.persistence.mongodb.JournallingFieldNames$;

/**
 * Provides {@link MongoPersistenceOperationsSelection}s for selecting/deleting documents in a MongoDB EventSource
 * persistence.
 */
@Immutable
final class MongoPersistenceOperationsSelectionProvider {

    private static final String PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    private final MongoEventSourceSettings settings;

    private MongoPersistenceOperationsSelectionProvider(final MongoEventSourceSettings settings) {
        this.settings = checkNotNull(settings, "MongoEventSourceSettings");
    }

    /**
     * Returns an instance of {@code MongoPersistenceOperationsSelectionProvider}.
     *
     * @param settings the MongoDB EventSource settings.
     * @return the instance.
     * @throws NullPointerException if {@code settings} is {@code null}.
     */
    public static MongoPersistenceOperationsSelectionProvider of(final MongoEventSourceSettings settings) {
        return new MongoPersistenceOperationsSelectionProvider(settings);
    }

    /**
     * Select an entity by its ID.
     *
     * @param entityId the ID.
     * @return a collection of {@link MongoPersistenceOperationsSelection} which represent all occurrence of the entity
     * in the EventSource.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     */
    public Collection<MongoPersistenceOperationsSelection> selectEntity(final String entityId) {
        checkNotNull(entityId, "entity ID");

        if (settings.isSupportsNamespaces()) {
            validateAndExtractNamespace(entityId);
        }

        return settings.getSuffixSeparator()
                .map(suffixSeparator -> selectEntityBySuffix(entityId))
                .orElseGet(() -> selectEntityWithoutSuffix(entityId));
    }

    /**
     * Select a complete namespace.
     *
     * @param namespace the namespace.
     * @return a collection of {@link MongoPersistenceOperationsSelection} which represent all occurrence of a namespace
     * in the EventSource.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     */
    public Collection<MongoPersistenceOperationsSelection> selectNamespace(final CharSequence namespace) {
        checkNotNull(namespace, "namespace");

        if (!settings.isSupportsNamespaces()) {
            throw new UnsupportedOperationException("Namespaces are not supported!");
        }

        return settings.getSuffixSeparator()
                .map(suffixSeparator -> selectNamespaceWithSuffix(namespace))
                .orElseGet(() -> selectNamespaceWithoutSuffix(namespace));
    }

    private Collection<MongoPersistenceOperationsSelection> selectEntityBySuffix(final String entityId) {
        return Collections.unmodifiableList(Arrays.asList(
                selectEntityByPid(settings.getMetadataCollectionName(), entityId),
                // collection "*Metadata" has no namespace suffix
                selectEntityBySuffix(settings.getJournalCollectionName(), entityId),
                selectEntityBySuffix(settings.getSnapshotCollectionName(), entityId)));
    }

    private Collection<MongoPersistenceOperationsSelection> selectEntityWithoutSuffix(final String entityId) {
        return Collections.unmodifiableList(Arrays.asList(
                selectEntityByPid(settings.getMetadataCollectionName(), entityId),
                selectEntityByPid(settings.getJournalCollectionName(), entityId),
                selectEntityByPid(settings.getSnapshotCollectionName(), entityId)));
    }

    private Collection<MongoPersistenceOperationsSelection> selectNamespaceWithSuffix(final CharSequence namespace) {
        return Collections.unmodifiableList(Arrays.asList(
                selectNamespaceByPid(settings.getMetadataCollectionName(), namespace),
                // collection "*Metadata" has no namespace suffix
                selectNamespaceBySuffix(settings.getJournalCollectionName(), namespace),
                selectNamespaceBySuffix(settings.getSnapshotCollectionName(), namespace)));
    }

    private Collection<MongoPersistenceOperationsSelection> selectNamespaceWithoutSuffix(final CharSequence namespace) {
        return Collections.unmodifiableList(Arrays.asList(
                selectNamespaceByPid(settings.getMetadataCollectionName(), namespace),
                selectNamespaceByPid(settings.getJournalCollectionName(), namespace),
                selectNamespaceByPid(settings.getSnapshotCollectionName(), namespace)));
    }

    private MongoPersistenceOperationsSelection selectNamespaceBySuffix(final String collection,
            final CharSequence namespace) {

        final String suffixSeparator = settings.getSuffixSeparator().orElseThrow(IllegalStateException::new);
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        return MongoPersistenceOperationsSelection.of(suffixedCollection, new Document());
    }

    private MongoPersistenceOperationsSelection selectNamespaceByPid(final String collection,
            final CharSequence namespace) {

        return MongoPersistenceOperationsSelection.of(collection, filterByPidPrefix(namespace));
    }

    private Document filterByPidPrefix(final CharSequence namespace) {
        final String pidRegex = String.format("^%s%s:", settings.getPersistenceIdPrefix(), namespace);
        return new Document(PID, new BsonRegularExpression(pidRegex));
    }

    private MongoPersistenceOperationsSelection selectEntityBySuffix(final String collection, final String entityId) {
        final String namespace = validateAndExtractNamespace(entityId);
        final String suffixSeparator = settings.getSuffixSeparator().orElseThrow(IllegalStateException::new);
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        final Document filter = filterByPid(entityId);
        return MongoPersistenceOperationsSelection.of(suffixedCollection, filter);
    }

    private static String validateAndExtractNamespace(final String entityId) {
        final int separatorIndex = entityId.indexOf(':');
        if (-1 == separatorIndex) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Entity ID <{0}> does not have namespace!", entityId));
        }

        return entityId.substring(0, separatorIndex);
    }

    private MongoPersistenceOperationsSelection selectEntityByPid(final String collection, final String entityId) {
        return MongoPersistenceOperationsSelection.of(collection, filterByPid(entityId));
    }

    private Document filterByPid(final String entityId) {
        final String pid = String.format("%s%s", settings.getPersistenceIdPrefix(), entityId);
        return new Document(PID, new BsonString(pid));
    }

}
