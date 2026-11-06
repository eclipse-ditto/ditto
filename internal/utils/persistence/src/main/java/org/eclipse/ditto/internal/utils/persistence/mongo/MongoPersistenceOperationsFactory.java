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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsFactory;
import org.eclipse.ditto.internal.utils.persistence.api.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.api.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEntitiesPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEventSourceSettings;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoNamespacePersistenceOperations;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

/**
 * MongoDB implementation of {@link PersistenceOperationsFactory}.
 * <p>
 * For each entity type it builds the persistence-operations collaborators a service's ops actor used to build inline,
 * preserving the historical per-service wiring exactly:
 * <ul>
 *     <li>{@code thing} &rarr; {@link MongoNamespacePersistenceOperations} only — mirrors
 *     {@code ThingPersistenceOperationsActor.props} (persistence-id prefix {@code "thing:"}, supports namespaces),</li>
 *     <li>{@code policy} &rarr; both {@link MongoNamespacePersistenceOperations} and
 *     {@link MongoEntitiesPersistenceOperations} — mirrors {@code PolicyPersistenceOperationsActor.props}
 *     (prefix {@code "policy:"}, supports namespaces),</li>
 *     <li>{@code connection} &rarr; {@link MongoEntitiesPersistenceOperations} only — mirrors
 *     {@code ConnectionPersistenceOperationsActor.props} (prefix {@code "connection:"}, does NOT support
 *     namespaces).</li>
 * </ul>
 * The collaborators read the journal/snapshot collection names from {@link MongoEventSourceSettings#fromConfig} against
 * the same {@code actorSystem.settings().config()} the service actors used, and the journal/snapshot plugin IDs come
 * from the owning {@link MongoPersistenceBackendProvider} (so plugin-ID resolution stays single-sourced).
 * <p>
 * <strong>Laziness (load-bearing):</strong> {@link #operations(String)} constructs a fresh {@link MongoClientWrapper}
 * on every call — exactly as {@code MongoClientWrapper.newInstance(mongoDbConfig)} ran at ops-actor instantiation
 * today. It therefore MUST be invoked lazily, at ops-actor instantiation time (it runs inside the ops actor's
 * {@code Props} factory lambda), not when the {@code Props} is built. The client is the bundle's
 * {@link PersistenceOperationsCollaborators#closeable()} so each ops actor owns and closes its own client on
 * {@code postStop}; this factory never caches or shares a client across calls.
 *
 * @since 4.0.0
 */
@Immutable
public final class MongoPersistenceOperationsFactory implements PersistenceOperationsFactory {

    private static final String THING = "thing";
    private static final String POLICY = "policy";
    private static final String CONNECTION = "connection";

    private final Config config;
    private final MongoDbConfig mongoDbConfig;
    private final Function<String, String> journalPluginIdResolver;
    private final Function<String, String> snapshotPluginIdResolver;

    private MongoPersistenceOperationsFactory(final Config config,
            final Function<String, String> journalPluginIdResolver,
            final Function<String, String> snapshotPluginIdResolver) {

        this.config = config;
        this.mongoDbConfig = DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
        this.journalPluginIdResolver = journalPluginIdResolver;
        this.snapshotPluginIdResolver = snapshotPluginIdResolver;
    }

    /**
     * Creates a factory delegating plugin-ID resolution to the owning Mongo provider (the production path).
     *
     * @param actorSystem the actor system whose {@code settings().config()} provides the per-entity event-source
     * collection overrides and the MongoDB config — exactly the {@code config} the service ops actors used.
     * @param provider the owning provider, used to resolve the per-entity journal/snapshot plugin IDs (single-sourced).
     * @return the factory.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MongoPersistenceOperationsFactory of(final ActorSystem actorSystem,
            final MongoPersistenceBackendProvider provider) {

        Objects.requireNonNull(actorSystem, "actorSystem");
        Objects.requireNonNull(provider, "provider");
        return new MongoPersistenceOperationsFactory(actorSystem.settings().config(),
                provider::getJournalPluginId, provider::getSnapshotPluginId);
    }

    /**
     * Creates a factory directly from an actor system and config, resolving plugin IDs with a fresh
     * {@link MongoPersistenceBackendProvider} over the same config. Convenience for tests and for callers that do not
     * already hold a provider; the production provider uses {@link #of(ActorSystem, MongoPersistenceBackendProvider)}.
     *
     * @param actorSystem the actor system the extension is loaded into.
     * @param config the config providing per-entity event-source overrides, the MongoDB config and the
     * {@code persistence-backend-provider} extension config.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MongoPersistenceOperationsFactory(final ActorSystem actorSystem, final Config config) {
        this(Objects.requireNonNull(config, "config"),
                resolverProvider(Objects.requireNonNull(actorSystem, "actorSystem"), config));
    }

    private MongoPersistenceOperationsFactory(final Config config, final MongoPersistenceBackendProvider provider) {
        this(config, provider::getJournalPluginId, provider::getSnapshotPluginId);
    }

    private static MongoPersistenceBackendProvider resolverProvider(final ActorSystem actorSystem,
            final Config config) {
        final String path = "ditto.extensions.persistence-backend-provider.extension-config";
        final Config extensionConfig =
                config.hasPath(path) ? config.getConfig(path) : com.typesafe.config.ConfigFactory.empty();
        return new MongoPersistenceBackendProvider(actorSystem, extensionConfig);
    }

    @Override
    public PersistenceOperationsCollaborators operations(final String entityType) {
        final EntityWiring wiring = EntityWiring.forEntityType(entityType);

        final MongoEventSourceSettings eventSourceSettings = MongoEventSourceSettings.fromConfig(config,
                wiring.persistenceIdPrefix, wiring.supportsNamespaces,
                journalPluginIdResolver.apply(entityType), snapshotPluginIdResolver.apply(entityType));

        // Build a fresh client per call (one per ops actor) — see the laziness note on the class javadoc.
        final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        final MongoDatabase db = mongoClient.getDefaultDatabase();

        @Nullable final NamespacePersistenceOperations namespaceOps = wiring.namespaceOps
                ? MongoNamespacePersistenceOperations.of(db, eventSourceSettings)
                : null;
        @Nullable final EntityPersistenceOperations entitiesOps = wiring.entitiesOps
                ? MongoEntitiesPersistenceOperations.of(db, eventSourceSettings)
                : null;

        return PersistenceOperationsCollaborators.of(namespaceOps, entitiesOps, mongoClient);
    }

    /**
     * Per-entity wiring, kept identical to the historical per-service ops actors.
     */
    private enum EntityWiring {

        // persistenceIdPrefix, supportsNamespaces, buildNamespaceOps, buildEntitiesOps
        THING_WIRING(THING + ":", true, true, false),
        POLICY_WIRING(POLICY + ":", true, true, true),
        CONNECTION_WIRING(CONNECTION + ":", false, false, true);

        private final String persistenceIdPrefix;
        private final boolean supportsNamespaces;
        private final boolean namespaceOps;
        private final boolean entitiesOps;

        EntityWiring(final String persistenceIdPrefix, final boolean supportsNamespaces, final boolean namespaceOps,
                final boolean entitiesOps) {
            this.persistenceIdPrefix = persistenceIdPrefix;
            this.supportsNamespaces = supportsNamespaces;
            this.namespaceOps = namespaceOps;
            this.entitiesOps = entitiesOps;
        }

        private static EntityWiring forEntityType(final String entityType) {
            switch (Objects.requireNonNull(entityType, "entityType")) {
                case THING:
                    return THING_WIRING;
                case POLICY:
                    return POLICY_WIRING;
                case CONNECTION:
                    return CONNECTION_WIRING;
                default:
                    throw new IllegalArgumentException(
                            "No Mongo persistence-operations wiring known for entity type <" + entityType + ">");
            }
        }
    }

}
