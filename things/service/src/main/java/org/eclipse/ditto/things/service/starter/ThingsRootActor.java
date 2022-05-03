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
package org.eclipse.ditto.things.service.starter;

import static org.eclipse.ditto.things.api.ThingsMessagingConstants.CLUSTER_ROLE;

import java.util.Optional;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespacesUpdater;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.PersistenceCleanupActor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.ThingEventPubSubFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.placeholders.PlaceholderSubstitution;
import org.eclipse.ditto.policies.enforcement.validators.CommandWithOptionalEntityValidator;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.ThingsConfig;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActorPropsFactory;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceOperationsActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingsPersistenceStreamingActorCreator;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class ThingsRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsRoot";

    private static final String DEFAULT_NAMESPACE = "org.eclipse.ditto"; // TODO TJ fix after merge from master

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    @SuppressWarnings("unused")
    private ThingsRootActor(final ThingsConfig thingsConfig,
            final ActorRef pubSubMediator,
            final ThingPersistenceActorPropsFactory propsFactory) {

        final var actorSystem = getContext().system();

        final var clusterConfig = thingsConfig.getClusterConfig();
        final var shardRegionExtractor =
                ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem);
        final var distributedAcks = DistributedAcks.lookup(actorSystem);
        final var pubSubFactory =
                ThingEventPubSubFactory.of(getContext(), shardRegionExtractor, distributedAcks);
        final DistributedPub<ThingEvent<?>> distributedPub = pubSubFactory.startDistributedPub();

        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);

        final ActorRef policiesShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                PoliciesMessagingConstants.CLUSTER_ROLE,
                PoliciesMessagingConstants.SHARD_REGION
        );

        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        // start cluster singleton that writes to the distributed cache of blocked namespaces
        final Props blockedNamespacesUpdaterProps = BlockedNamespacesUpdater.props(blockedNamespaces, pubSubMediator);
        ClusterUtil.startSingleton(actorSystem, getContext(), CLUSTER_ROLE,
                BlockedNamespacesUpdater.ACTOR_NAME, blockedNamespacesUpdaterProps);

        final Props thingSupervisorActorProps = getThingSupervisorActorProps(pubSubMediator,
                policiesShardRegion,
                distributedPub,
                propsFactory,
                blockedNamespaces
        );
        final ActorRef thingsShardRegion = ClusterSharding.get(actorSystem)
                .start(ThingsMessagingConstants.SHARD_REGION,
                        thingSupervisorActorProps,
                        ClusterShardingSettings.create(actorSystem).withRole(CLUSTER_ROLE),
                        shardRegionExtractor
                );

        startChildActor(ThingPersistenceOperationsActor.ACTOR_NAME,
                ThingPersistenceOperationsActor.props(pubSubMediator, thingsConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), thingsConfig.getPersistenceOperationsConfig()));

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(thingsShardRegion,
                ThingsMessagingConstants.SHARD_REGION, log);

        final var healthCheckConfig = thingsConfig.getHealthCheckConfig();
        final var hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        if (healthCheckConfig.getPersistenceConfig().isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final var healthCheckingActorOptions = hcBuilder.build();
        final var metricsReporterConfig =
                healthCheckConfig.getPersistenceConfig().getMetricsReporterConfig();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props()));

        final ActorRef snapshotStreamingActor =
                ThingsPersistenceStreamingActorCreator.startSnapshotStreamingActor(this::startChildActor);

        final var cleanupConfig = thingsConfig.getThingConfig().getCleanupConfig();
        final var mongoReadJournal = newMongoReadJournal(thingsConfig.getMongoDbConfig(), actorSystem);
        final Props cleanupActorProps = PersistenceCleanupActor.props(cleanupConfig, mongoReadJournal, CLUSTER_ROLE);
        startChildActor(PersistenceCleanupActor.NAME, cleanupActorProps);

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());
        pubSubMediator.tell(DistPubSubAccess.put(snapshotStreamingActor), getSelf());

        bindHttpStatusRoute(thingsConfig.getHttpConfig(), healthCheckingActor);
    }

    /**
     * Creates Akka configuration object Props for this ThingsRootActor.
     *
     * @param thingsConfig the configuration settings of the Things service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param propsFactory factory of Props of thing-persistence-actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ThingsConfig thingsConfig,
            final ActorRef pubSubMediator,
            final ThingPersistenceActorPropsFactory propsFactory) {

        return Props.create(ThingsRootActor.class, thingsConfig, pubSubMediator, propsFactory);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .build().orElse(super.createReceive());
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the things shard as requested ...");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private static Props getThingSupervisorActorProps(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final DistributedPub<ThingEvent<?>> distributedPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            final BlockedNamespaces blockedNamespaces) {

        return ThingSupervisorActor.props(pubSubMediator, policiesShardRegion, distributedPub, propsFactory,
                blockedNamespaces, providePreEnforcer(blockedNamespaces));
    }

    private static PreEnforcer providePreEnforcer(final BlockedNamespaces blockedNamespaces) {
        return newPreEnforcer(blockedNamespaces, PlaceholderSubstitution.newInstance());
        // TODO TJ provide extension mechanism here
    }

    /**
     * TODO TJ consolidate with PoliciesRootActor.newPreEnforcer
     * @param blockedNamespaces
     * @param placeholderSubstitution
     * @return
     */
    private static PreEnforcer newPreEnforcer(final BlockedNamespaces blockedNamespaces,
            final PlaceholderSubstitution placeholderSubstitution) {

        return dittoHeadersSettable ->
                BlockNamespaceBehavior.of(blockedNamespaces)
                        .block(dittoHeadersSettable)
                        .thenApply(CommandWithOptionalEntityValidator.getInstance())
                        .thenApply(ThingsRootActor::prependDefaultNamespaceToCreateThing)
                        .thenApply(ThingsRootActor::setOriginatorHeader)
                        .thenCompose(placeholderSubstitution);
    }

    private static DittoHeadersSettable<?> prependDefaultNamespaceToCreateThing(final DittoHeadersSettable<?> signal) {
        if (signal instanceof CreateThing createThing) {
            final Thing thing = createThing.getThing();
            final Optional<String> namespace = thing.getNamespace();
            if (namespace.isEmpty()) {
                final Thing thingInDefaultNamespace = thing.toBuilder()
                        .setId(ThingId.of(DEFAULT_NAMESPACE, createThing.getEntityId().toString()))
                        .build();
                final JsonObject initialPolicy = createThing.getInitialPolicy().orElse(null);
                return CreateThing.of(thingInDefaultNamespace, initialPolicy, createThing.getDittoHeaders());
            }
        }
        return signal;
    }

    /**
     * Set the "ditto-originator" header to the primary authorization subject of a signal.
     *
     * @param originalSignal A signal with authorization context.
     * @return A copy of the signal with the header "ditto-originator" set.
     */
    @SuppressWarnings("unchecked")
    public static <T extends DittoHeadersSettable<?>> T setOriginatorHeader(final T originalSignal) {
        final DittoHeaders dittoHeaders = originalSignal.getDittoHeaders();
        final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();
        return authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(originatorSubjectId -> DittoHeaders.newBuilder(dittoHeaders)
                        .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), originatorSubjectId)
                        .build())
                .map(originatorHeader -> (T) originalSignal.setDittoHeaders(originatorHeader))
                .orElse(originalSignal);
    }

    private static MongoReadJournal newMongoReadJournal(final MongoDbConfig mongoDbConfig,
            final ActorSystem actorSystem) {

        final var config = actorSystem.settings().config();
        final var mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        return MongoReadJournal.newInstance(config, mongoClient, actorSystem);
    }

}
