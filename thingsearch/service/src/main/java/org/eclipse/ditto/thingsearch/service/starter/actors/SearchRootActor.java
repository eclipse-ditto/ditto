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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.BACKGROUND_SYNC_COLLECTION_NAME;

import org.eclipse.ditto.base.service.RootChildActorStarter;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.internal.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoTimestampPersistence;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.query.QueryParser;
import org.eclipse.ditto.thingsearch.service.persistence.query.validation.QueryCriteriaValidator;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.thingsearch.service.updater.actors.SearchUpdaterRootActor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.SystemMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class SearchRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = ThingsSearchConstants.ROOT_ACTOR_NAME;

    private final LoggingAdapter log;

    @SuppressWarnings("unused")
    private SearchRootActor(final SearchConfig searchConfig, final ActorRef pubSubMediator) {

        final var actorSystem = getContext().getSystem();
        log = Logging.getLogger(actorSystem, this);

        final var mongoDbConfig = searchConfig.getMongoDbConfig();
        final var monitoringConfig = mongoDbConfig.getMonitoringConfig();

        final DittoMongoClient mongoDbClient = MongoClientExtension.get(actorSystem).getSearchClient();
        RootChildActorStarter.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()))
                .execute(getContext());

        final var thingsSearchPersistence =
                getThingsSearchPersistence(searchConfig, mongoDbClient);
        final ActorRef searchActor = initializeSearchActor(searchConfig, thingsSearchPersistence, pubSubMediator);
        pubSubMediator.tell(DistPubSubAccess.put(searchActor), getSelf());

        final TimestampPersistence backgroundSyncPersistence =
                MongoTimestampPersistence.initializedInstance(BACKGROUND_SYNC_COLLECTION_NAME, mongoDbClient,
                        SystemMaterializer.get(actorSystem).materializer());

        final ActorRef searchUpdaterRootActor = startChildActor(SearchUpdaterRootActor.ACTOR_NAME,
                SearchUpdaterRootActor.props(searchConfig, pubSubMediator, thingsSearchPersistence,
                        backgroundSyncPersistence));
        final ActorRef healthCheckingActor = initializeHealthCheckActor(searchConfig, searchUpdaterRootActor);

        bindHttpStatusRoute(searchConfig.getHttpConfig(), healthCheckingActor);
    }

    static QueryParser getQueryParser(final SearchConfig searchConfig, final ActorSystem actorSystem) {
        final var limitsConfig = searchConfig.getLimitsConfig();
        final var fieldExpressionFactory = getThingsFieldExpressionFactory(searchConfig);
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(limitsConfig);
        final var queryCriteriaValidator =
                QueryCriteriaValidator.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()));
        return QueryParser.of(fieldExpressionFactory, queryBuilderFactory, queryCriteriaValidator);
    }

    private MongoThingsSearchPersistence getThingsSearchPersistence(final SearchConfig searchConfig,
            final DittoMongoClient mongoDbClient) {

        final ActorContext context = getContext();
        final var persistenceConfig = searchConfig.getQueryPersistenceConfig();
        final var persistence = new MongoThingsSearchPersistence(mongoDbClient, context.getSystem(), persistenceConfig);

        final var indexInitializationConfig = searchConfig.getIndexInitializationConfig();
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

    private ActorRef initializeHealthCheckActor(final SearchConfig searchConfig,
            final ActorRef searchUpdaterRootActor) {
        return startChildActor(SearchHealthCheckingActorFactory.ACTOR_NAME,
                SearchHealthCheckingActorFactory.props(searchConfig, searchUpdaterRootActor));
    }

    /**
     * Creates Akka configuration object Props for this SearchRootActor.
     *
     * @param searchConfig the configuration settings of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final SearchConfig searchConfig, final ActorRef pubSubMediator) {
        return Props.create(SearchRootActor.class, searchConfig, pubSubMediator);
    }

    private static ThingsFieldExpressionFactory getThingsFieldExpressionFactory(final SearchConfig searchConfig) {
        return ThingsFieldExpressionFactory.of(searchConfig.getSimpleFieldMappings());
    }

    private ActorRef initializeSearchActor(final SearchConfig searchConfig,
            final ThingsSearchPersistence thingsSearchPersistence, final ActorRef pubSubMediator) {
        final var queryParser = getQueryParser(searchConfig, getContext().getSystem());
        final var props = SearchActor.props(queryParser, thingsSearchPersistence, pubSubMediator);
        return startChildActor(SearchActor.ACTOR_NAME, props);
    }

}
