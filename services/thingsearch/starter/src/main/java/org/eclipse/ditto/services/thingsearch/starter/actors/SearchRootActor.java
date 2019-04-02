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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

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
import org.eclipse.ditto.services.base.config.HttpConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryParser;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.starter.actors.health.SearchHealthCheckingActorFactory;
import org.eclipse.ditto.services.thingsearch.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoTimestampPersistence;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.reactivestreams.client.MongoDatabase;
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

        final DittoMongoClient dittoMongoClient = MongoClientWrapper.getBuilder(MongoConfig.of(rawConfig))
                .addCommandListener(kamonCommandListener)
                .addConnectionPoolListener(kamonConnectionPoolListener)
                .build();

        final TimestampPersistence thingsSyncPersistence =
                MongoTimestampPersistence.initializedInstance(THINGS_SYNC_STATE_COLLECTION_NAME, dittoMongoClient,
                        materializer);

        final TimestampPersistence policiesSyncPersistence =
                MongoTimestampPersistence.initializedInstance(POLICIES_SYNC_STATE_COLLECTION_NAME, dittoMongoClient,
                        materializer);

        final ActorRef searchActor = initializeSearchActor(configReader, dittoMongoClient.getDefaultDatabase());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(searchActor), getSelf());

        final ActorRef healthCheckingActor =
                initializeHealthCheckActor(configReader, thingsSyncPersistence, policiesSyncPersistence);

        createHealthCheckingActorHttpBinding(configReader.http(), healthCheckingActor, materializer);

        startChildActor(SearchUpdaterRootActor.ACTOR_NAME, SearchUpdaterRootActor.props(configReader, pubSubMediator,
                materializer, thingsSyncPersistence, policiesSyncPersistence));
    }

    private ActorRef initializeSearchActor(final ServiceConfigReader configReader, final MongoDatabase database) {

        final Config rawConfig = configReader.getRawConfig();
        final ThingsSearchPersistence thingsSearchPersistence =
                initializeSearchPersistence(database, getContext().getSystem(), rawConfig);

        final boolean indexInitializationEnabled = rawConfig.getBoolean(ConfigKeys.INDEX_INITIALIZATION_ENABLED);
        if (indexInitializationEnabled) {
            thingsSearchPersistence.initializeIndices();
        } else {
            log.info("Skipping IndexInitializer because it is disabled.");
        }

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory expressionFactory = getThingsFieldExpressionFactory();
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(configReader.limits());
        final QueryParser queryFactory = QueryParser.of(criteriaFactory, expressionFactory, queryBuilderFactory);

        final Props searchActorProps = SearchActor.props(queryFactory, thingsSearchPersistence);

        return startChildActor(SearchActor.ACTOR_NAME, searchActorProps);
    }

    private ThingsSearchPersistence initializeSearchPersistence(final MongoDatabase db, final ActorSystem actorSystem,
            final Config config) {

        // TODO: refactor config.
        final String hintsConfigKey = "ditto.things-search.mongo-hints-by-namespace";
        final MongoThingsSearchPersistence persistence = new MongoThingsSearchPersistence(db, actorSystem);
        if (config.hasPath(hintsConfigKey)) {
            final String mongoHints = config.getString(hintsConfigKey);
            log.info("Applying MongoDB hints: {}", mongoHints);
            return persistence.withHintsByNamespace(mongoHints);
        } else {
            return persistence;
        }
    }

    private ActorRef initializeHealthCheckActor(final ServiceConfigReader configReader,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        return startChildActor(SearchHealthCheckingActorFactory.ACTOR_NAME,
                SearchHealthCheckingActorFactory.props(configReader, thingsSyncPersistence, policiesSyncPersistence));
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

    private static void addMapping(final Map<String, String> fieldMappings, final JsonFieldDefinition<?> definition) {
        final JsonPointer pointer = definition.getPointer();
        final String key = pointer.getRoot().map(JsonKey::toString).orElse("");
        final String value = pointer.toString();
        fieldMappings.put(key, value);
    }

    private static ThingsFieldExpressionFactory getThingsFieldExpressionFactory() {
        final Map<String, String> mappings = new HashMap<>();
        mappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        mappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
        addMapping(mappings, Thing.JsonFields.POLICY_ID);
        addMapping(mappings, Thing.JsonFields.REVISION);
        addMapping(mappings, Thing.JsonFields.MODIFIED);
        return new ThingsFieldExpressionFactoryImpl(mappings);
    }
}
