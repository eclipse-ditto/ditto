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

import javax.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.eclipse.ditto.base.service.RootChildActorStarter;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.connectivity.service.config.ConnectionIdsRetrievalConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.enforcement.ConnectionEnforcerActorPropsFactory;
import org.eclipse.ditto.connectivity.service.messaging.ConnectionIdsRetrievalActor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceOperationsActor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceStreamingActorCreator;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionSupervisorActor;
import org.eclipse.ditto.edge.service.dispatching.EdgeCommandForwarderActor;
import org.eclipse.ditto.edge.service.dispatching.ShardRegions;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
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
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.PersistencePingActor;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.PersistenceCleanupActor;
import org.eclipse.ditto.internal.utils.pubsubthings.DittoProtocolSub;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
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
        // Create persistence streaming actor (with no cache) and make it known to pubSubMediator.
        final ActorRef persistenceStreamingActor =
                startChildActor(ConnectionPersistenceStreamingActorCreator.ACTOR_NAME,
                        ConnectionPersistenceStreamingActorCreator.props());
        pubSubMediator.tell(DistPubSubAccess.put(persistenceStreamingActor), getSelf());


        // start DittoProtocolSub and blocked namespaces extensions, even if not passed to connections via reference
        //  because of serialization issues the single BaseClientActors "get" the extension themselves
        //  it must however be started here in order to already participate in Ditto pub/sub, even if no connection is
        //  available!
        log.info("Started blocked namespaces replicator <{}>", BlockedNamespaces.of(actorSystem).getReplicator());
        DittoProtocolSub.get(actorSystem);

        final MongoReadJournal mongoReadJournal = MongoReadJournal.newInstance(actorSystem);

        final var connectionSupervisorProps =
                ConnectionSupervisorActor.props(commandForwarder, pubSubMediator, enforcerActorPropsFactory,
                        mongoReadJournal);
        startClusterSingletonActor(
                PersistencePingActor.props(
                        startConnectionShardRegion(actorSystem, connectionSupervisorProps, clusterConfig),
                        connectivityConfig.getPingConfig(), mongoReadJournal),
                PersistencePingActor.ACTOR_NAME);
        final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig =
                connectivityConfig.getConnectionIdsRetrievalConfig();
        startChildActor(ConnectionIdsRetrievalActor.ACTOR_NAME, ConnectionIdsRetrievalActor.props(mongoReadJournal,
                connectionIdsRetrievalConfig));

        startChildActor(ConnectionPersistenceOperationsActor.ACTOR_NAME,
                ConnectionPersistenceOperationsActor.props(pubSubMediator, connectivityConfig.getMongoDbConfig(),
                        config, connectivityConfig.getPersistenceOperationsConfig()));

        RootChildActorStarter.get(actorSystem, ScopedConfig.dittoExtension(config)).execute(getContext());


        final var cleanupConfig = connectivityConfig.getConnectionConfig().getCleanupConfig();
        final var cleanupActorProps = PersistenceCleanupActor.props(cleanupConfig, mongoReadJournal, CLUSTER_ROLE);
        startChildActor(PersistenceCleanupActor.ACTOR_NAME, cleanupActorProps);

        final ActorRef healthCheckingActor = getHealthCheckingActor(connectivityConfig);
        bindHttpStatusRoute(connectivityConfig.getHttpConfig(), healthCheckingActor);
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param connectivityConfig the configuration of the Connectivity service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Akka configuration Props object.
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

    private ActorRef startClusterSingletonActor(final Props props, final String name) {
        return ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, name, props);
    }

    private ActorRef getHealthCheckingActor(final ConnectivityConfig connectivityConfig) {
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
                        MongoHealthChecker.props()
                ));
    }

    private ActorRef getCommandForwarder(final ClusterConfig clusterConfig, final ActorRef pubSubMediator) {
        return startChildActor(EdgeCommandForwarderActor.ACTOR_NAME,
                EdgeCommandForwarderActor.props(pubSubMediator,
                        ShardRegions.of(getContext().getSystem(), clusterConfig)));
    }

    private static ActorRef startConnectionShardRegion(final ActorSystem actorSystem,
            final Props connectionSupervisorProps, final ClusterConfig clusterConfig) {

        final ClusterShardingSettings shardingSettings = ClusterShardingSettings.create(actorSystem)
                .withRole(ConnectivityMessagingConstants.CLUSTER_ROLE);

        return ClusterSharding.get(actorSystem)
                .start(ConnectivityMessagingConstants.SHARD_REGION,
                        connectionSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));
    }

}
