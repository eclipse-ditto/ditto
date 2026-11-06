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

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsFactory;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.SnapshotStreamingActor;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.payload.MongoPersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;

import com.typesafe.config.Config;

/**
 * MongoDB-backed implementation of {@link PersistenceBackendProvider}. Yields the standard
 * {@code pekko-contrib-mongodb-persistence-*} plugin IDs and wraps a shared
 * {@link MongoReadJournal} instance loaded from the Ditto MongoDB configuration.
 * <p>
 * Plugin IDs are resolved as {@code pekko-contrib-mongodb-persistence-<blockName>-journal} and
 * {@code ...-<blockName>-snapshots}, where {@code blockName} comes from the {@link #WELL_KNOWN_BLOCK_NAMES}
 * map of the shipped Mongo plugin block per entity type (NOT a pluralization heuristic — see that field's
 * Javadoc for why). Services may override individual mappings via the extension config:
 * <pre>
 * ditto.extensions.persistence-backend-provider = {
 *   extension-class = "org.eclipse.ditto.internal.utils.persistence.mongo.MongoPersistenceBackendProvider"
 *   extension-config {
 *     plugin-ids {
 *       connection.journal  = "pekko-contrib-mongodb-persistence-connection-journal"
 *       connection.snapshot = "pekko-contrib-mongodb-persistence-connection-snapshots"
 *     }
 *   }
 * }
 * </pre>
 */
public final class MongoPersistenceBackendProvider implements PersistenceBackendProvider {

    private static final String PLUGIN_ID_PREFIX = "pekko-contrib-mongodb-persistence-";
    private static final String JOURNAL_SUFFIX = "-journal";
    private static final String SNAPSHOT_SUFFIX = "-snapshots";
    private static final String PLUGIN_IDS_PATH = "plugin-ids";
    private static final String JOURNAL_KEY = "journal";
    private static final String SNAPSHOT_KEY = "snapshot";

    /**
     * The shipped Mongo plugin BLOCK NAMES per entity type — the single in-code source of truth, immune to
     * extension-config loss (the string-shorthand extension declaration replaces the reference.conf object
     * with an empty config). The former pluralize() heuristic produced non-existent blocks ("policys") and
     * is removed: an unknown entity type without an explicit override is a hard DittoConfigError.
     */
    private static final Map<String, String> WELL_KNOWN_BLOCK_NAMES = Map.of(
            "thing", "things",
            "policy", "policies",
            "connection", "connection",
            "wot-validation-config", "wot-validation-config");

    private final ActorSystem actorSystem;
    private final Config extensionConfig;
    @Nullable
    private DittoReadJournal readJournal;
    @Nullable
    private PersistenceOperationsFactory operationsFactory;

    /**
     * Constructor invoked reflectively by {@code PekkoClassLoader} during extension loading.
     * <p>
     * The {@link DittoReadJournal} is created lazily on first call to {@link #getReadJournal()}
     * because constructing a {@link MongoReadJournal} requires a fully-configured
     * {@code pekko-contrib-mongodb-persistence-*} HOCON block, which isn't present in tests that
     * use the in-memory persistence plugin. Lazy construction lets plugin-ID lookups (which all
     * actors do during Pekko's {@code Eventsourced} trait init) work even in those test setups.
     *
     * @param actorSystem the actor system the extension is loaded into
     * @param extensionConfig the {@code extension-config} subtree of the extension declaration
     */
    public MongoPersistenceBackendProvider(final ActorSystem actorSystem, final Config extensionConfig) {
        this.actorSystem = Objects.requireNonNull(actorSystem, "actorSystem");
        this.extensionConfig = Objects.requireNonNull(extensionConfig, "extensionConfig");
    }

    @Override
    public String getJournalPluginId(final String entityType) {
        return getOverride(entityType, JOURNAL_KEY)
                .orElseGet(() -> PLUGIN_ID_PREFIX + blockName(entityType) + JOURNAL_SUFFIX);
    }

    @Override
    public String getSnapshotPluginId(final String entityType) {
        return getOverride(entityType, SNAPSHOT_KEY)
                .orElseGet(() -> PLUGIN_ID_PREFIX + blockName(entityType) + SNAPSHOT_SUFFIX);
    }

    @Override
    public PersistencePluginConfig pluginConfig() {
        return MongoPersistencePluginConfig.of(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to a {@link MongoPersistenceOperationsFactory}, which builds the per-entity collaborators around a
     * fresh {@code MongoClientWrapper}. The factory itself holds no client, so it is created once and reused; the
     * client is built per {@code operations(entityType)} call (one per ops actor) and handed back as the bundle's
     * {@code closeable}. As documented on {@link PersistenceOperationsFactory}, this MUST be invoked lazily at
     * ops-actor instantiation time so exactly one client is created per ops actor (matching the historical behaviour).
     */
    @Override
    public PersistenceOperationsCollaborators operations(final String entityType) {
        return getOperationsFactory().operations(entityType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@link MongoHealthChecker#props()} — the same no-arg {@link Props} factory that every
     * Ditto service's {@code RootActor} uses today via
     * {@code DefaultHealthCheckingActorFactory.props(options, MongoHealthChecker.props())}.
     */
    @Override
    public Props healthCheck() {
        return MongoHealthChecker.props();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Builds a dedicated {@link MongoReadJournal} and {@link DittoMongoClient} for the streaming actor using exactly the
     * same arguments the actor used to construct internally before this refactoring
     * ({@code MongoReadJournal.newInstance(config, mongoClient, readJournalConfig, system)}), and returns the
     * backend-neutral {@link SnapshotStreamingActor} props with the Mongo client as the {@link java.io.Closeable} the
     * actor closes on stop. The resources are rebuilt inside the supplier passed to the per-incarnation
     * {@link SnapshotStreamingActor#props(Function, Function, java.util.function.Supplier)} overload — NOT captured
     * once here — so that every actor incarnation (including supervised restarts) gets its own client: Pekko's
     * default {@code preRestart} runs {@code postStop}, which closes the client, so a captured instance would be
     * permanently closed after the first restart. This matches the historical behaviour of the previously
     * self-constructing actor.
     */
    @Override
    public Props streaming(final String entityType,
            final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid) {
        return SnapshotStreamingActor.props(pid2EntityId, entityId2Pid, () -> {
            // Rebuilt per incarnation: postStop (also invoked by preRestart) closes the client, so a
            // captured instance would be permanently closed after the first supervised restart.
            final Config config = actorSystem.settings().config();
            final MongoDbConfig mongoDbConfig = DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
            final DittoMongoClient mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
            final MongoReadJournal mongoReadJournal = MongoReadJournal.newInstance(config, mongoClient,
                    mongoDbConfig.getReadJournalConfig(), actorSystem);
            return new SnapshotStreamingActor.StreamingResources(mongoReadJournal, mongoClient);
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the singleton {@link MongoSnapshotCodec} — the BSON envelope extracted from
     * {@link AbstractMongoSnapshotAdapter}. Both the adapter and this accessor share the same stateless
     * codec instance, so the BSON envelope logic lives in exactly one place.
     */
    @Override
    public SnapshotCodec snapshotCodec() {
        return MongoSnapshotCodec.INSTANCE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a fresh {@link MongoPersistedPayloadStore} over the {@code <entityType>_snaps},
     * {@code <entityType>_journal} and {@code <entityType>_encryption_migration} collections, built around its own
     * {@link MongoClientWrapper} from the {@code ditto.mongodb} block of the actor system's config — the same block a
     * service's own {@code MongoDbConfig} is read from (see {@link #streaming}). One client per call, closed by the
     * store's {@code close()}; nothing is cached or shared, so the laziness rule of the interface applies: call it from
     * the using actor's {@code preStart}.
     */
    @Override
    public PersistedPayloadStore payloadStore(final String entityType) {
        return MongoPersistedPayloadStore.newInstance(entityType, actorSystem);
    }

    private synchronized PersistenceOperationsFactory getOperationsFactory() {
        if (operationsFactory == null) {
            operationsFactory = MongoPersistenceOperationsFactory.of(actorSystem, this);
        }
        return operationsFactory;
    }

    @Override
    public synchronized DittoReadJournal getReadJournal() {
        if (readJournal == null) {
            readJournal = MongoReadJournal.newInstance(actorSystem);
        }
        return readJournal;
    }

    private java.util.Optional<String> getOverride(final String entityType, final String key) {
        final String path = PLUGIN_IDS_PATH + "." + entityType + "." + key;
        if (extensionConfig.hasPath(path)) {
            return java.util.Optional.of(extensionConfig.getString(path));
        }
        return java.util.Optional.empty();
    }

    private static String blockName(final String entityType) {
        final String known = WELL_KNOWN_BLOCK_NAMES.get(entityType);
        if (known == null) {
            throw new DittoConfigError("No Mongo persistence plugin block is known for entity type <"
                    + entityType + "> and no extension-config.plugin-ids override is configured. "
                    + "Declare plugin-ids." + entityType + ".{journal|snapshot} in "
                    + "ditto.extensions.persistence-backend-provider.extension-config.");
        }
        return known;
    }

}
