/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICIES_SYNC_STATE_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_SYNC_STATE_COLLECTION_NAME;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.base.config.HttpConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoAggregationBuilderFactory;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.query.actors.AggregationQueryActor;
import org.eclipse.ditto.services.thingsearch.query.actors.QueryActor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilderFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.starter.actors.health.SearchHealthCheckingActorFactory;
import org.eclipse.ditto.services.thingsearch.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoSearchSyncPersistence;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.typesafe.config.Config;

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

    private static final String KAMON_METRICS_PREFIX = "search";

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsSearchRoot";

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);


    private SearchRootActor(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        final Config rawConfig = configReader.getRawConfig();
        final CommandListener kamonCommandListener = rawConfig.getBoolean(ConfigKeys.MONITORING_COMMANDS_ENABLED) ?
                new KamonCommandListener(KAMON_METRICS_PREFIX) : null;
        final ConnectionPoolListener kamonConnectionPoolListener =
                rawConfig.getBoolean(ConfigKeys.MONITORING_CONNECTION_POOL_ENABLED) ?
                        new KamonConnectionPoolListener(KAMON_METRICS_PREFIX) : null;

        final MongoClientWrapper mongoClientWrapper =
                MongoClientWrapper.newInstance(rawConfig, kamonCommandListener, kamonConnectionPoolListener);

        final StreamMetadataPersistence thingsSyncPersistence =
                MongoSearchSyncPersistence.initializedInstance(THINGS_SYNC_STATE_COLLECTION_NAME, mongoClientWrapper,
                        materializer);

        final StreamMetadataPersistence policiesSyncPersistence =
                MongoSearchSyncPersistence.initializedInstance(POLICIES_SYNC_STATE_COLLECTION_NAME, mongoClientWrapper,
                        materializer);

        final ActorRef searchActor = initializeSearchActor(configReader, mongoClientWrapper);

        final ActorRef healthCheckingActor = initializeHealthCheckActor(configReader, mongoClientWrapper,
                thingsSyncPersistence, policiesSyncPersistence);

        pubSubMediator.tell(new DistributedPubSubMediator.Put(searchActor), getSelf());

        createHealthCheckingActorHttpBinding(configReader.http(), healthCheckingActor, materializer);

        startChildActor(SearchUpdaterRootActor.ACTOR_NAME, SearchUpdaterRootActor.props(configReader, pubSubMediator,
                materializer, thingsSyncPersistence, policiesSyncPersistence));
    }

    private ActorRef initializeSearchActor(final ServiceConfigReader configReader, final MongoClientWrapper
            mongoClientWrapper) {

        final Config rawConfig = configReader.getRawConfig();
        final ThingsSearchPersistence thingsSearchPersistence =
                new MongoThingsSearchPersistence(mongoClientWrapper, getContext().system());

        final boolean indexInitializationEnabled = rawConfig.getBoolean(ConfigKeys.INDEX_INITIALIZATION_ENABLED);
        if (indexInitializationEnabled) {
            thingsSearchPersistence.initializeIndices();
        } else {
            log.info("Skipping IndexInitializer because it is disabled.");
        }

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory = new ThingsFieldExpressionFactoryImpl();
        final AggregationBuilderFactory aggregationBuilderFactory =
                new MongoAggregationBuilderFactory(configReader.limits());
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(configReader.limits());
        final ActorRef aggregationQueryActor = startChildActor(AggregationQueryActor.ACTOR_NAME,
                AggregationQueryActor.props(criteriaFactory, fieldExpressionFactory, aggregationBuilderFactory));
        final ActorRef apiV1QueryActor = startChildActor(QueryActor.ACTOR_NAME,
                QueryActor.props(criteriaFactory, fieldExpressionFactory, queryBuilderFactory));

        return startChildActor(SearchActor.ACTOR_NAME,
                SearchActor.props(aggregationQueryActor, apiV1QueryActor, thingsSearchPersistence));
    }

    private ActorRef initializeHealthCheckActor(final ServiceConfigReader configReader, final MongoClientWrapper mongoClientWrapper,
            final StreamMetadataPersistence thingsSyncPersistence,
            final StreamMetadataPersistence policiesSyncPersistence) {
        final ActorRef mongoHealthCheckActor = startChildActor(MongoReactiveHealthCheckActor.ACTOR_NAME,
                MongoReactiveHealthCheckActor.props(mongoClientWrapper));

        return startChildActor(SearchHealthCheckingActorFactory.ACTOR_NAME,
                SearchHealthCheckingActorFactory.props(configReader, mongoHealthCheckActor, thingsSyncPersistence,
                        policiesSyncPersistence));
    }

    private void createHealthCheckingActorHttpBinding(final HttpConfigReader httpConfig,
            final ActorRef healthCheckingActor, final ActorMaterializer materializer) {
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system()) //
                .bindAndHandle(
                        createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                                materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint..");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        ).exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            getContext().system().terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this SearchRootActor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return Props.create(SearchRootActor.class, new Creator<SearchRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchRootActor create() {
                return new SearchRootActor(configReader, pubSubMediator, materializer);
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
