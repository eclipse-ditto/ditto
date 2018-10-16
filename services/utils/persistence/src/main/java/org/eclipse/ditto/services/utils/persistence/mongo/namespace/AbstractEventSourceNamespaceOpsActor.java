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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Arrays;
import java.util.Collection;

import org.bson.BsonRegularExpression;
import org.bson.Document;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;

/**
 * Superclass of actors operating on an event-sourcing MongoDB persistence at the level of namespaces.
 */
public abstract class AbstractEventSourceNamespaceOpsActor extends AbstractNamespaceOpsActor {

    private static final String PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    // realtime journal is not touched because it is a capped collection
    private final String metadata;
    private final String journal;
    private final String snapshot;
    private final String suffixSeparator;
    private final boolean isSuffixBuilderEnabled;

    /**
     * Create a new instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param db Database where the event journal, snapshot store and metadata are located.
     * @param config Configuration with info about the event journal, snapshot store, metadata and suffix builder.
     */
    protected AbstractEventSourceNamespaceOpsActor(final ActorRef pubSubMediator, final MongoDatabase db,
            final Config config) {

        super(pubSubMediator, MongoNamespaceOps.of(db));
        metadata = getCollectionName(config, journalPluginId(), "metadata");
        journal = getCollectionName(config, journalPluginId(), "journal");
        snapshot = getCollectionName(config, snapshotPluginId(), "snaps");
        suffixSeparator = readConfig(config, suffixBuilderPath("separator"), "@");
        isSuffixBuilderEnabled = !readConfig(config, suffixBuilderPath("class"), "").trim().isEmpty();
    }

    /**
     * @return Akka persistence ID prefix of the resource type this actor operates on.
     * @see AbstractNamespaceOpsActor#resourceType()
     */
    protected abstract String persistenceIdPrefix();

    /**
     * @return The journal plugin ID - namely the config key at which the event journal configuration is found.
     */
    protected abstract String journalPluginId();

    /**
     * @return The snapshot plugin ID - namely the config key at which the snapshot store configuration is found.
     */
    protected abstract String snapshotPluginId();

    @Override
    protected Collection<MongoNamespaceSelection> selectNamespace(final String namespace) {
        return isSuffixBuilderEnabled
                ? selectNamespaceWithSuffixBuilder(namespace)
                : selectNamespaceWithoutSuffixBuilder(namespace);
    }

    private Collection<MongoNamespaceSelection> selectNamespaceWithSuffixBuilder(final String namespace) {
        return Arrays.asList(
                selectByPid(metadata, namespace),
                selectBySuffix(journal, namespace),
                selectBySuffix(snapshot, namespace));
    }

    private Collection<MongoNamespaceSelection> selectNamespaceWithoutSuffixBuilder(final String namespace) {
        return Arrays.asList(
                selectByPid(metadata, namespace),
                selectByPid(journal, namespace),
                selectByPid(snapshot, namespace));
    }

    private MongoNamespaceSelection selectBySuffix(final String collection, final String namespace) {
        final String suffixedCollection = String.format("%s%s%s", collection, suffixSeparator, namespace);
        return MongoNamespaceSelection.of(suffixedCollection, new Document());
    }

    private MongoNamespaceSelection selectByPid(final String collection, final String namespace) {
        return MongoNamespaceSelection.of(collection, filterByPid(namespace));
    }

    private Document filterByPid(final String namespace) {
        final String pidRegex = String.format("^%s%s:", persistenceIdPrefix(), namespace);
        return new Document().append(PID, new BsonRegularExpression(pidRegex));
    }

    private static String getCollectionName(final Config config, final String root, final String collectionType) {
        final String path = String.format("%s.overrides.%s-collection", root, collectionType);
        return config.getString(path);
    }

    private static String readConfig(final Config config, final String path, final String fallback) {
        return config.hasPath(path) ? config.getString(path) : fallback;
    }

    private static String suffixBuilderPath(final String key) {
        return "akka.contrib.persistence.mongodb.mongo.suffix-builder." + key;
    }

}
