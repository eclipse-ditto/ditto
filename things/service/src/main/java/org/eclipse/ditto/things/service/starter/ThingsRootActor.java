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

import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.ddata.ORSet;
import org.apache.pekko.cluster.ddata.ORSetKey;
import org.apache.pekko.cluster.ddata.Replicator;
import org.apache.pekko.event.DiagnosticLoggingAdapter;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.service.RootChildActorStarter;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionCreator;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.PersistenceCleanupActor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsubthings.ThingEventPubSubFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProviderExtension;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfig;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.aggregation.DefaultThingsAggregatorConfig;
import org.eclipse.ditto.things.service.aggregation.ThingsAggregatorActor;
import org.eclipse.ditto.things.service.aggregation.ThingsAggregatorConfig;
import org.eclipse.ditto.things.service.common.config.ThingsConfig;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActorPropsFactory;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceOperationsActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingsPersistenceStreamingActorCreator;
import org.eclipse.ditto.things.service.persistence.actors.WotValidationConfigSupervisorActor;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.WotValidationConfigDData;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.WotValidationConfigUtils;
import org.eclipse.ditto.wot.api.validator.WotThingModelValidator;
import org.eclipse.ditto.wot.integration.DittoWotIntegration;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class ThingsRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsRoot";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    private final ActorSystem actorSystem;

    private final DittoWotIntegration dittoWotIntegration;
    private final TmValidationConfig staticWotConfig;
    private final Executor wotDispatcher;

    @SuppressWarnings("unused")
    private ThingsRootActor(final ThingsConfig thingsConfig, final ActorRef pubSubMediator,
            final ThingPersistenceActorPropsFactory propsFactory) {
        this.actorSystem = getContext().getSystem();

        final var clusterConfig = thingsConfig.getClusterConfig();
        final int numberOfShards = clusterConfig.getNumberOfShards();
        final var shardRegionExtractor = ShardRegionExtractor.of(numberOfShards, actorSystem);
        final var distributedAcks = DistributedAcks.lookup(actorSystem);
        final var pubSubFactory =
                ThingEventPubSubFactory.of(getContext(), shardRegionExtractor, distributedAcks);
        final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin = pubSubFactory.startDistributedPub();
        final LiveSignalPub liveSignalPub = LiveSignalPub.of(getContext(), distributedAcks);

        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        final PolicyEnforcerProvider policyEnforcerProvider = PolicyEnforcerProviderExtension.get(actorSystem).getPolicyEnforcerProvider();
        final var mongoReadJournal = newMongoReadJournal(thingsConfig.getMongoDbConfig(), actorSystem);
        final Props thingSupervisorActorProps = getThingSupervisorActorProps(pubSubMediator,
                distributedPubThingEventsForTwin,
                liveSignalPub,
                propsFactory,
                blockedNamespaces,
                policyEnforcerProvider,
                mongoReadJournal
        );

        final ActorRef thingsShardRegion =
                ShardRegionCreator.start(actorSystem, ThingsMessagingConstants.SHARD_REGION, thingSupervisorActorProps,
                        numberOfShards, CLUSTER_ROLE);

        // Create WoT validation config supervisor actor
        final Props wotValidationConfigSupervisorProps = WotValidationConfigSupervisorActor.props(
                pubSubMediator,
                mongoReadJournal
        );
        final ActorRef wotValidationConfigShardRegion =
                ShardRegionCreator.start(
                        actorSystem,
                        "wot-validation-config",
                        wotValidationConfigSupervisorProps,
                        shardRegionExtractor,
                        CLUSTER_ROLE
                );

        // Ensure the default WoT validation config is loaded from persistence and DData is repopulated
        final RetrieveWotValidationConfig retrieveCmd =
                RetrieveWotValidationConfig.of(org.eclipse.ditto.things.model.devops.WotValidationConfigId.GLOBAL, org.eclipse.ditto.base.model.headers.DittoHeaders.empty());
        wotValidationConfigShardRegion.tell(retrieveCmd, ActorRef.noSender());

        startChildActor(ThingPersistenceOperationsActor.ACTOR_NAME,
                ThingPersistenceOperationsActor.props(pubSubMediator, thingsConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), thingsConfig.getPersistenceOperationsConfig()));

        final ThingsAggregatorConfig thingsAggregatorConfig = DefaultThingsAggregatorConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );

        final Props props = ThingsAggregatorActor.props(thingsShardRegion, thingsAggregatorConfig, pubSubMediator);
        startChildActor(ThingsAggregatorActor.ACTOR_NAME, props);

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
                ThingsPersistenceStreamingActorCreator.startPersistenceStreamingActor(this::startChildActor);

        final var cleanupConfig = thingsConfig.getThingConfig().getCleanupConfig();
        final Props cleanupActorProps = PersistenceCleanupActor.props(cleanupConfig, mongoReadJournal, CLUSTER_ROLE);
        startChildActor(PersistenceCleanupActor.ACTOR_NAME, cleanupActorProps);

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());

        bindHttpStatusRoute(thingsConfig.getHttpConfig(), healthCheckingActor);

        // Initialize WoT integration first
        dittoWotIntegration = DittoWotIntegration.get(actorSystem);
        staticWotConfig = dittoWotIntegration.getWotConfig().getValidationConfig();
        wotDispatcher = actorSystem.dispatchers().lookup(DittoWotIntegration.WOT_DISPATCHER);

        // Initialize WoT validator with static config (will be replaced by merged config in initializeWotValidationConfig)
        // Will be set in initializeWotValidationConfig

        // Subscribe to DData changes and initialize config
        final WotValidationConfigDData wotValidationConfigDData = WotValidationConfigDData.of(actorSystem);
        wotValidationConfigDData.subscribeForChanges(getSelf());
        log.debug("Subscribed for WoT validation config DData changes");

        initializeWotValidationConfig();

        RootChildActorStarter.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()))
                .execute(getContext());
    }

    /**
     * Creates Pekko configuration object Props for this ThingsRootActor.
     *
     * @param thingsConfig the configuration settings of the Things service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param propsFactory factory of Props of thing-persistence-actor.
     * @return the Pekko configuration Props object.
     */
    public static Props props(final ThingsConfig thingsConfig, final ActorRef pubSubMediator,
            final ThingPersistenceActorPropsFactory propsFactory) {
        return Props.create(ThingsRootActor.class, thingsConfig, pubSubMediator, propsFactory);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .match(Replicator.Changed.class, this::handleWotValidationConfigChanged)
                .build().orElse(super.createReceive());
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the things shard as requested ...");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private void handleWotValidationConfigChanged(final Replicator.Changed<?> event) {
        log.debug("Received DData change for key: {}", event.key());
        if (event.key().equals(ORSetKey.create("WotValidationConfig"))) {
            final Set<JsonObject> newConfigs = ((ORSet<JsonObject>) event.dataValue()).getElements();
            log.debug("Processing {} config change(s). Configs: {}", newConfigs.size(), newConfigs);
            try {
                final TmValidationConfig mergedConfig;
                if (!newConfigs.isEmpty()) {
                    // Get the first config and merge with static config
                    JsonObject firstJson = newConfigs.iterator().next();
                    var firstConfig = WotValidationConfig.fromJson(firstJson);
                    mergedConfig = WotValidationConfigUtils.mergeConfigsToTmValidationConfig(firstConfig,
                            staticWotConfig);
                } else {
                    mergedConfig = staticWotConfig;
                }

                // this returns a singleton instance of WotThingModelValidator
                WotThingModelValidator.of(
                        dittoWotIntegration.getWotThingModelResolver(),
                        wotDispatcher,
                        mergedConfig
                ).updateConfig(mergedConfig);
                log.debug("Updated validator with merged config");
            } catch (Exception e) {
                log.error("Error processing config change: {}", e.getMessage(), e);
            }
        }
    }

    private static Props getThingSupervisorActorProps(final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin,
            final LiveSignalPub liveSignalPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {
        return ThingSupervisorActor.props(pubSubMediator, distributedPubThingEventsForTwin,
                liveSignalPub, propsFactory, blockedNamespaces, policyEnforcerProvider, mongoReadJournal);
    }

    private static MongoReadJournal newMongoReadJournal(final MongoDbConfig mongoDbConfig,
            final ActorSystem actorSystem) {
        final var config = actorSystem.settings().config();
        final var mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);

        return MongoReadJournal.newInstance(config, mongoClient, mongoDbConfig.getReadJournalConfig(), actorSystem);
    }

    private void initializeWotValidationConfig() {
        // Get the static config from WotConfig
        log.info("Initialized static WoT validation config: enabled={}, logWarningInsteadOfFailingApiCalls={}",
                staticWotConfig.isEnabled(), staticWotConfig.logWarningInsteadOfFailingApiCalls());

        // Get DData instance and subscribe to config changes
        final WotValidationConfigDData ddata = WotValidationConfigDData.of(actorSystem);
        ddata.getConfigs().thenAccept(configs -> {
            final TmValidationConfig mergedConfig;
            if (!configs.isEmpty()) {
                // Get the first config since we only expect one
                final JsonObject configJson = configs.getElements().iterator().next();
                final WotValidationConfig config = WotValidationConfig.fromJson(configJson);
                mergedConfig = WotValidationConfigUtils.mergeConfigsToTmValidationConfig(config, staticWotConfig);
            } else {
                mergedConfig = staticWotConfig;
            }

            // this returns a singleton instance of WotThingModelValidator
            WotThingModelValidator.of(
                    dittoWotIntegration.getWotThingModelResolver(),
                    wotDispatcher,
                    mergedConfig
            ).updateConfig(mergedConfig);
            log.info("Initialized WoT validator with merged config");
        });
        log.info("Subscribed to WoT validation config changes");
    }

}
