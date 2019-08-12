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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICIES_SYNC_STATE_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_SYNC_STATE_COLLECTION_NAME;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.thingsearch.common.config.SearchConfig;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryParser;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
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
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
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

    private final LoggingAdapter log;
    private final SupervisorStrategy supervisorStrategy;

    @SuppressWarnings("unused")
    private SearchRootActor(final SearchConfig searchConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        log = Logging.getLogger(getContext().system(), this);
        supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

        final MongoDbConfig mongoDbConfig = searchConfig.getMongoDbConfig();
        final MongoDbConfig.MonitoringConfig monitoringConfig = mongoDbConfig.getMonitoringConfig();

        final DittoMongoClient mongoDbClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                .addCommandListener(getCommandListenerOrNull(monitoringConfig))
                .addConnectionPoolListener(getConnectionPoolListenerOrNull(monitoringConfig))
                .build();

        final ThingsSearchPersistence thingsSearchPersistence = getThingsSearchPersistence(searchConfig, mongoDbClient);
        final ActorRef searchActor = initializeSearchActor(searchConfig.getLimitsConfig(), thingsSearchPersistence);
        pubSubMediator.tell(DistPubSubAccess.put(searchActor), getSelf());

        final TimestampPersistence thingsSyncPersistence =
                MongoTimestampPersistence.initializedInstance(THINGS_SYNC_STATE_COLLECTION_NAME, mongoDbClient,
                        materializer);

        final TimestampPersistence policiesSyncPersistence =
                MongoTimestampPersistence.initializedInstance(POLICIES_SYNC_STATE_COLLECTION_NAME, mongoDbClient,
                        materializer);

        final ActorRef healthCheckingActor =
                initializeHealthCheckActor(searchConfig, thingsSyncPersistence, policiesSyncPersistence);

        createHealthCheckingActorHttpBinding(searchConfig.getHttpConfig(), healthCheckingActor, materializer);

        startChildActor(SearchUpdaterRootActor.ACTOR_NAME,
                SearchUpdaterRootActor.props(searchConfig, pubSubMediator, materializer, thingsSyncPersistence,
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

    private ThingsSearchPersistence getThingsSearchPersistence(final SearchConfig searchConfig,
            final DittoMongoClient mongoDbClient) {

        final ActorContext context = getContext();
        final MongoThingsSearchPersistence persistence =
                new MongoThingsSearchPersistence(mongoDbClient, context.getSystem());

        final IndexInitializationConfig indexInitializationConfig = searchConfig.getIndexInitializationConfig();
        if (indexInitializationConfig.isIndexInitializationConfigEnabled()) {
            persistence.initializeIndices();
        } else {
            log.info("Skipping IndexInitializer because it is disabled.");
        }

        return searchConfig.getMongoHintsByNamespace()
                .map(mongoHintsByNamespace -> {
                    log.info("Applying MongoDB hints <{}>.", mongoHintsByNamespace);
                    return persistence.withHintsByNamespace(mongoHintsByNamespace);
                })
                .orElse(persistence);
    }

    private ActorRef initializeSearchActor(final LimitsConfig limitsConfig,
            final ThingsSearchPersistence thingsSearchPersistence) {

        final QueryParser queryParser = getQueryParser(limitsConfig);

        return startChildActor(SearchActor.ACTOR_NAME, SearchActor.props(queryParser, thingsSearchPersistence));
    }

    static QueryParser getQueryParser(final LimitsConfig limitsConfig) {
        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory = getThingsFieldExpressionFactory();
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(limitsConfig);
        return QueryParser.of(criteriaFactory, fieldExpressionFactory, queryBuilderFactory);
    }

    private ActorRef initializeHealthCheckActor(final SearchConfig searchConfig,
            final TimestampPersistence thingsSyncPersistence, final TimestampPersistence policiesSyncPersistence) {

        return startChildActor(SearchHealthCheckingActorFactory.ACTOR_NAME,
                SearchHealthCheckingActorFactory.props(searchConfig, thingsSyncPersistence, policiesSyncPersistence));
    }

    private void createHealthCheckingActorHttpBinding(final HttpConfig httpConfig,
            final ActorRef healthCheckingActor, final ActorMaterializer materializer) {

        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
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

        return Props.create(SearchRootActor.class, searchConfig, pubSubMediator, materializer);
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
        log.info("Starting child actor <{}>.", actorName);
        return getContext().actorOf(props, actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

    private static ThingsFieldExpressionFactory getThingsFieldExpressionFactory() {
        final Map<String, String> mappings = new HashMap<>(5);
        mappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        mappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
        addMapping(mappings, Thing.JsonFields.POLICY_ID);
        addMapping(mappings, Thing.JsonFields.REVISION);
        addMapping(mappings, Thing.JsonFields.MODIFIED);
        return new ThingsFieldExpressionFactoryImpl(mappings);
    }

    private static void addMapping(final Map<String, String> fieldMappings, final JsonFieldDefinition<?> definition) {
        final JsonPointer pointer = definition.getPointer();
        final String key = pointer.getRoot().map(JsonKey::toString).orElse("");
        final String value = pointer.toString();
        fieldMappings.put(key, value);
    }

}
