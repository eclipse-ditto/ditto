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
package org.eclipse.ditto.connectivity.service;

import jakarta.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;
import org.apache.pekko.event.DiagnosticLoggingAdapter;
import org.apache.pekko.cluster.singleton.ClusterSingletonProxy;
import org.apache.pekko.cluster.singleton.ClusterSingletonProxySettings;
import org.apache.pekko.japi.pf.DeciderBuilder;
import org.eclipse.ditto.base.service.RootChildActorStarter;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.connectivity.service.config.ConnectionIdsRetrievalConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.enforcement.ConnectionEnforcerActorPropsFactory;
import org.eclipse.ditto.connectivity.service.messaging.ConnectionIdsRetrievalActor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceOperationsActor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceStreamingActorCreator;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionSupervisorActor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.EncryptionMigrationActor;
import org.eclipse.ditto.edge.service.dispatching.EdgeCommandForwarderActor;
import org.eclipse.ditto.edge.service.dispatching.ShardRegions;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.config.PersistenceConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.eclipse.ditto.internal.utils.persistentactors.PersistencePingActor;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.PersistenceCleanupActor;
import org.eclipse.ditto.internal.utils.pubsubthings.DittoProtocolSub;

import com.typesafe.config.Config;

import scala.PartialFunction;

/**
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class ConnectivityRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "connectivityRoot";

    private static final String CLUSTER_ROLE = "connectivity";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @SuppressWarnings("unused")
    private ConnectivityRootActor(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator) {

        final ClusterConfig clusterConfig = connectivityConfig.getClusterConfig();
        final ActorSystem actorSystem = getContext().system();
        final Config config = actorSystem.settings().config();

        final ActorRef commandForwarder = getCommandForwarder(clusterConfig, pubSubMediator);

        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        final var enforcerActorPropsFactory =
                ConnectionEnforcerActorPropsFactory.get(actorSystem, dittoExtensionsConfig);

        final PersistenceBackendProvider backendProvider =
                PersistenceBackendProvider.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()));
        // Boot-time plugin-wiring check, before anything touches the database: a backend that owns one (PostgreSQL)
        // refuses to boot when the deployment HOCON still wires another backend's journal/snapshot plugins; the
        // default backend (MongoDB) runs no check.
        backendProvider.verifyPluginWiring(ConnectivityService.SERVICE_NAME);
        // Bootstrap the persistence backend's schema BEFORE the read journal / persistent-actor shard region start.
        // Mongo is a no-op (default); Postgres creates+verifies its tables, waits a bounded, configured time while the
        // database is not reachable yet, and throws on any other failure, so the service never serves traffic against
        // a database whose tables are absent and were never bootstrapped.
        backendProvider.bootstrapSchema();
        final DittoReadJournal readJournal = backendProvider.getReadJournal();

        // Create persistence streaming actor (with no cache) and make it known to pubSubMediator.
        final ActorRef persistenceStreamingActor =
                startChildActor(ConnectionPersistenceStreamingActorCreator.ACTOR_NAME,
                        ConnectionPersistenceStreamingActorCreator.props(readJournal));
        pubSubMediator.tell(DistPubSubAccess.put(persistenceStreamingActor), getSelf());


        // start DittoProtocolSub and blocked namespaces extensions, even if not passed to connections via reference
        //  because of serialization issues the single BaseClientActors "get" the extension themselves
        //  it must however be started here in order to already participate in Ditto pub/sub, even if no connection is
        //  available!
        log.info("Started blocked namespaces replicator <{}>", BlockedNamespaces.of(actorSystem).getReplicator());
        DittoProtocolSub.get(actorSystem);

        final var connectionSupervisorProps =
                ConnectionSupervisorActor.props(commandForwarder, pubSubMediator, connectivityConfig,
                        enforcerActorPropsFactory, readJournal);
        startClusterSingletonActor(
                PersistencePingActor.props(
                        startConnectionShardRegion(actorSystem, connectionSupervisorProps, clusterConfig,
                                backendProvider),
                        connectivityConfig.getPingConfig(), readJournal),
                PersistencePingActor.ACTOR_NAME);
        final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig =
                connectivityConfig.getConnectionIdsRetrievalConfig();
        startChildActor(ConnectionIdsRetrievalActor.ACTOR_NAME, ConnectionIdsRetrievalActor.props(readJournal,
                connectionIdsRetrievalConfig));

        startChildActor(ConnectionPersistenceOperationsActor.ACTOR_NAME,
                ConnectionPersistenceOperationsActor.props(pubSubMediator, backendProvider,
                        connectivityConfig.getPersistenceOperationsConfig()));

        // Encryption migration (DevOps commands at /user/connectivityRoot/encryptionMigration): a cluster singleton
        // that rewrites the connection journal and snapshots through the provider's PersistedPayloadStore, on every
        // backend, whenever encryption is enabled or an old key is set.
        optionallyStartEncryptionMigrationSingleton(actorSystem, connectivityConfig, backendProvider);

        RootChildActorStarter.get(actorSystem, ScopedConfig.dittoExtension(config)).execute(getContext());


        final var cleanupConfig = connectivityConfig.getConnectionConfig().getCleanupConfig();
        final var cleanupActorProps = PersistenceCleanupActor.props(cleanupConfig, readJournal, CLUSTER_ROLE);
        startChildActor(PersistenceCleanupActor.ACTOR_NAME, cleanupActorProps);

        final ActorRef healthCheckingActor = getHealthCheckingActor(connectivityConfig, backendProvider);
        bindHttpStatusRoute(connectivityConfig.getHttpConfig(), healthCheckingActor);
    }

    /**
     * Creates Pekko configuration object Props for this ConnectivityRootActor.
     *
     * @param connectivityConfig the configuration of the Connectivity service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Pekko configuration Props object.
     */
    public static Props props(final ConnectivityConfig connectivityConfig, final ActorRef pubSubMediator) {

        return Props.create(ConnectivityRootActor.class, connectivityConfig, pubSubMediator);
    }

    @Override
    protected PartialFunction<Throwable, SupervisorStrategy.Directive> getSupervisionDecider() {
        return DeciderBuilder.match(JMSRuntimeException.class, e -> {
            log.warning("JMSRuntimeException '{}' occurred.", e.getMessage());
            return restartChild();
        }).match(NamingException.class, e -> {
            log.warning("NamingException '{}' occurred.", e.getMessage());
            return restartChild();
        }).build().orElse(super.getSupervisionDecider());
    }

    private void optionallyStartEncryptionMigrationSingleton(final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig, final PersistenceBackendProvider backendProvider) {

        if (encryptionMigrationNeeded(connectivityConfig.getConnectionConfig().getFieldsEncryptionConfig())) {
            // The migration actor obtains its payload store (and the backend client the store owns) from the provider
            // when it starts (only on the node that runs the singleton) and closes it when it stops; the root actor
            // builds none.
            final String managerName = EncryptionMigrationActor.ACTOR_NAME + "Singleton";
            final ActorRef singletonManager = startClusterSingletonActor(
                    EncryptionMigrationActor.props(connectivityConfig, backendProvider), managerName);

            final ClusterSingletonProxySettings proxySettings =
                    ClusterSingletonProxySettings.create(actorSystem).withRole(CLUSTER_ROLE);
            final Props proxyProps = ClusterSingletonProxy.props(
                    singletonManager.path().toStringWithoutAddress(), proxySettings);
            getContext().actorOf(proxyProps, EncryptionMigrationActor.ACTOR_NAME);
        }
    }

    /**
     * Whether the connection encryption migration singleton (and its proxy) is started: whenever encryption is enabled
     * or an old key is set, since then an initial encryption, a key rotation or the disabling of encryption may be
     * triggered — on every persistence backend.
     *
     * @param encryptionConfig the connection fields encryption config.
     * @return {@code true} if the migration singleton is to be started.
     */
    static boolean encryptionMigrationNeeded(final FieldsEncryptionConfig encryptionConfig) {
        return encryptionConfig.isEncryptionEnabled() || encryptionConfig.getOldSymmetricalKey().isPresent();
    }

    private ActorRef startClusterSingletonActor(final Props props, final String name) {
        return ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, name, props);
    }

    private ActorRef getHealthCheckingActor(final ConnectivityConfig connectivityConfig,
            final PersistenceBackendProvider backendProvider) {
        final HealthCheckConfig healthCheckConfig = connectivityConfig.getHealthCheckConfig();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        final PersistenceConfig persistenceConfig = healthCheckConfig.getPersistenceConfig();
        if (persistenceConfig.isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }
        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();

        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions,
                        backendProvider.healthCheck()
                ));
    }

    private ActorRef getCommandForwarder(final ClusterConfig clusterConfig, final ActorRef pubSubMediator) {
        return startChildActor(EdgeCommandForwarderActor.ACTOR_NAME,
                EdgeCommandForwarderActor.props(pubSubMediator,
                        ShardRegions.of(getContext().getSystem(), clusterConfig)));
    }

    private static ActorRef startConnectionShardRegion(final ActorSystem actorSystem,
            final Props connectionSupervisorProps, final ClusterConfig clusterConfig,
            final PersistenceBackendProvider backendProvider) {

        final ClusterShardingSettings shardingSettings = rememberEntitiesShardingSettings(actorSystem,
                backendProvider.pluginConfig().getRememberStorePluginIds(ConnectivityMessagingConstants.SHARD_REGION));

        return ClusterSharding.get(actorSystem)
                .start(ConnectivityMessagingConstants.SHARD_REGION,
                        connectionSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));
    }

    /**
     * Builds the {@link ClusterShardingSettings} for the connection shard region, programmatically setting the
     * cluster-sharding {@code remember-entities} event-sourced store's journal/snapshot plugin IDs from the active
     * persistence backend's provider (the single source of truth — never hardcoded here).
     * <p>
     * On MongoDB they are the dedicated {@code pekko-contrib-mongodb-persistence-connection-remember-*} plugins
     * (behaviour-identical to the previously HOCON-only configuration); on PostgreSQL the dedicated
     * {@code ditto-postgres-connection-remember-*} plugins. Both feed Pekko's
     * {@code EventSourcedRememberEntitiesShardStore}, since {@code remember-entities-store} stays {@code eventsourced}
     * on either backend.
     *
     * @param actorSystem the actor system.
     * @param rememberStorePluginIds the remember-store journal/snapshot plugin IDs from the persistence provider.
     * @return the sharding settings carrying the provider's remember-store plugin IDs.
     */
    static ClusterShardingSettings rememberEntitiesShardingSettings(final ActorSystem actorSystem,
            final PersistencePluginConfig.RememberStorePluginIds rememberStorePluginIds) {

        return ClusterShardingSettings.create(actorSystem)
                .withRole(ConnectivityMessagingConstants.CLUSTER_ROLE)
                .withJournalPluginId(rememberStorePluginIds.journalPluginId())
                .withSnapshotPluginId(rememberStorePluginIds.snapshotPluginId());
    }

}
