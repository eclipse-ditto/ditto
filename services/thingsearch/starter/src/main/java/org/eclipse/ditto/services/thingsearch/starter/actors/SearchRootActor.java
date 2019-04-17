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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICIES_SYNC_STATE_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_SYNC_STATE_COLLECTION_NAME;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.base.config.HttpConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.query.AggregationQueryActor;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryActor;
import org.eclipse.ditto.services.thingsearch.persistence.read.AggregationBuilderFactory;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoAggregationBuilderFactory;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.starter.config.SearchConfig;
import org.eclipse.ditto.services.thingsearch.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoTimestampPersistence;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class SearchRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsSearchRoot";

    private static final String KAMON_METRICS_PREFIX = "search";

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

    private SearchRootActor(final SearchConfig searchConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        final MongoDbConfig mongoDbConfig = searchConfig.getMongoDbConfig();
        final MongoDbConfig.MonitoringConfig monitoringConfig = mongoDbConfig.getMonitoringConfig();

        final DittoMongoClient mongoDbClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                .addCommandListener(getCommandListenerOrNull(monitoringConfig))
                .addConnectionPoolListener(getConnectionPoolListenerOrNull(monitoringConfig))
                .build();

        final TimestampPersistence thingsSyncPersistence =
                MongoTimestampPersistence.initializedInstance(THINGS_SYNC_STATE_COLLECTION_NAME, mongoDbClient,
                        materializer);

        final TimestampPersistence policiesSyncPersistence =
                MongoTimestampPersistence.initializedInstance(POLICIES_SYNC_STATE_COLLECTION_NAME, mongoDbClient,
                        materializer);

        final ThingsSearchPersistence thingsSearchPersistence =
                getThingsSearchPersistence(mongoDbClient, searchConfig.getIndexInitializationConfig());
        final ActorRef searchActor = initializeSearchActor(searchConfig.getLimitsConfig(), thingsSearchPersistence);

        final ActorRef healthCheckingActor =
                initializeHealthCheckActor(searchConfig, thingsSyncPersistence, policiesSyncPersistence);

        pubSubMediator.tell(new DistributedPubSubMediator.Put(searchActor), getSelf());

        createHealthCheckingActorHttpBinding(searchConfig.getHttpConfig(), healthCheckingActor, materializer);

        startChildActor(SearchUpdaterRootActor.ACTOR_NAME,
                SearchUpdaterRootActor.props(searchConfig.getClusterConfig(), mongoDbConfig,
                        searchConfig.getIndexInitializationConfig(), searchConfig.getUpdaterConfig(),
                        searchConfig.getDeletionConfig(), pubSubMediator, materializer, thingsSyncPersistence,
                        policiesSyncPersistence));
    }

    @Nullable
    private static CommandListener getCommandListenerOrNull(final MongoDbConfig.MonitoringConfig monitoringConfig) {
        return monitoringConfig.isCommandsEnabled() ? new KamonCommandListener(KAMON_METRICS_PREFIX) : null;
    }

    @Nullable
    private static ConnectionPoolListener getConnectionPoolListenerOrNull(
            final MongoDbConfig.MonitoringConfig monitoringConfig) {

        return monitoringConfig.isConnectionPoolEnabled()
                ? new KamonConnectionPoolListener(KAMON_METRICS_PREFIX)
                : null;
    }

    private ThingsSearchPersistence getThingsSearchPersistence(final DittoMongoClient mongoClient,
            final IndexInitializationConfig indexInitializationConfig) {

        final ThingsSearchPersistence result = new MongoThingsSearchPersistence(mongoClient, getContext().system());
        if (indexInitializationConfig.isIndexInitializationConfigEnabled()) {
            result.initializeIndices();
        } else {
            log.info("Skipping IndexInitializer because it is disabled.");
        }
        return result;
    }

    private ActorRef initializeSearchActor(final LimitsConfig limitsConfig,
            final ThingsSearchPersistence thingsSearchPersistence) {

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory = new ThingsFieldExpressionFactoryImpl();
        final AggregationBuilderFactory aggregationBuilderFactory = new MongoAggregationBuilderFactory(limitsConfig);
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(limitsConfig);
        final ActorRef aggregationQueryActor = startChildActor(AggregationQueryActor.ACTOR_NAME,
                AggregationQueryActor.props(criteriaFactory, fieldExpressionFactory, aggregationBuilderFactory));
        final ActorRef apiV1QueryActor = startChildActor(QueryActor.ACTOR_NAME,
                QueryActor.props(criteriaFactory, fieldExpressionFactory, queryBuilderFactory));

        return startChildActor(SearchActor.ACTOR_NAME,
                SearchActor.props(aggregationQueryActor, apiV1QueryActor, thingsSearchPersistence));
    }

    private ActorRef initializeHealthCheckActor(final SearchConfig searchConfig,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        return startChildActor(SearchHealthCheckingActorFactory.ACTOR_NAME,
                SearchHealthCheckingActorFactory.props(searchConfig, thingsSyncPersistence, policiesSyncPersistence));
    }

    private void createHealthCheckingActorHttpBinding(final HttpConfig httpConfig,
            final ActorRef healthCheckingActor, final ActorMaterializer materializer) {

        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }

        final ActorSystem actorSystem = getContext().system();
        final CompletionStage<ServerBinding> binding = Http.get(actorSystem) //
                .bindAndHandle(
                        createRoute(actorSystem, healthCheckingActor).flow(actorSystem,
                                materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint ...");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        ).exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            actorSystem.terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this SearchRootActor.
     *
     * @param searchConfig the configuration settings of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    public static Props props(final SearchConfig searchConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return Props.create(SearchRootActor.class, new Creator<SearchRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchRootActor create() {
                return new SearchRootActor(searchConfig, pubSubMediator, materializer);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor '{}'", actorName);
        return getContext().actorOf(props, actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

}
