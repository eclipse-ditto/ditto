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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Arrays;
import java.util.Collection;

import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;

/**
 * Superclass of actors operating on an event-sourcing MongoDB persistence at the level of namespaces.
 */
public abstract class AbstractEventSourceNamespaceOpsActor extends AbstractNamespaceOpsActor<MongoNamespaceSelection> {

    private static final String PID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();

    // realtime journal is not touched because it is a capped collection
    private final String metadata;
    private final String journal;
    private final String snapshot;
    private final String suffixSeparator;
    private final boolean isSuffixBuilderEnabled;

    /**
     * Creates a new instance of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config configuration with info about the event journal, snapshot store, metadata and suffix builder.
     */
    protected AbstractEventSourceNamespaceOpsActor(final ActorRef pubSubMediator, final Config config) {
        super(pubSubMediator, MongoNamespaceOps.of(MongoClientWrapper.newInstance(config)));
        metadata = getCollectionName(config, getJournalPluginId(), "metadata");
        journal = getCollectionName(config, getJournalPluginId(), "journal");
        snapshot = getCollectionName(config, getSnapshotPluginId(), "snaps");
        suffixSeparator = readConfig(config, suffixBuilderPath("separator"), "@");
        isSuffixBuilderEnabled = !readConfig(config, suffixBuilderPath("class"), "").trim().isEmpty();
    }

    /**
     * @return Akka persistence ID prefix of the resource type this actor operates on.
     * @see AbstractNamespaceOpsActor#getResourceType()
     */
    protected abstract String getPersistenceIdPrefix();

    /**
     * @return The journal plugin ID - namely the config key at which the event journal configuration is found.
     */
    protected abstract String getJournalPluginId();

    /**
     * @return The snapshot plugin ID - namely the config key at which the snapshot store configuration is found.
     */
    protected abstract String getSnapshotPluginId();

    @Override
    protected Collection<MongoNamespaceSelection> selectNamespace(final String namespace) {
        return isSuffixBuilderEnabled
                ? selectNamespaceWithSuffixBuilder(namespace)
                : selectNamespaceWithoutSuffixBuilder(namespace);
    }

    private Collection<MongoNamespaceSelection> selectNamespaceWithSuffixBuilder(final String namespace) {
        return Arrays.asList(
                selectByPid(metadata, namespace), // collection "thingsMetadata" has no namespace suffix
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
        final String pidRegex = String.format("^%s%s:", getPersistenceIdPrefix(), namespace);
        return new Document(PID, new BsonRegularExpression(pidRegex));
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
