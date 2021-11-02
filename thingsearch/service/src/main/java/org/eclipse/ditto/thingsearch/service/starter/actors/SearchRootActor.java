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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoTimestampPersistence;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.Thing;
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
    public static final String ACTOR_NAME = "thingsSearchRoot";

    private final LoggingAdapter log;

    @SuppressWarnings("unused")
    private SearchRootActor(final SearchConfig searchConfig, final ActorRef pubSubMediator) {

        final var actorSystem = getContext().getSystem();
        log = Logging.getLogger(actorSystem, this);

        final var mongoDbConfig = searchConfig.getMongoDbConfig();
        final var monitoringConfig = mongoDbConfig.getMonitoringConfig();

        final DittoMongoClient mongoDbClient = MongoClientExtension.get(actorSystem).getSearchClient();

        final var thingsSearchPersistence = getThingsSearchPersistence(searchConfig, mongoDbClient);
        final ActorRef searchActor = initializeSearchActor(searchConfig.getLimitsConfig(), thingsSearchPersistence);
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

    static QueryParser getQueryParser(final LimitsConfig limitsConfig, final ActorSystem actorSystem) {
        final var fieldExpressionFactory = getThingsFieldExpressionFactory();
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(limitsConfig);
        final var queryCriteriaValidator = QueryCriteriaValidator.get(actorSystem);
        return QueryParser.of(fieldExpressionFactory, queryBuilderFactory, queryCriteriaValidator);
    }

    private static void addMapping(final Map<String, String> fieldMappings, final JsonFieldDefinition<?> definition) {
        final JsonPointer pointer = definition.getPointer();
        final String key = pointer.getRoot().map(JsonKey::toString).orElse("");
        final var value = pointer.toString();
        fieldMappings.put(key, value);
    }

    private ThingsSearchPersistence getThingsSearchPersistence(final SearchConfig searchConfig,
            final DittoMongoClient mongoDbClient) {

        final ActorContext context = getContext();
        final var persistence =
                new MongoThingsSearchPersistence(mongoDbClient, context.getSystem());

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

    private static ThingsFieldExpressionFactory getThingsFieldExpressionFactory() {
        // Not possible to use ModelBasedThingsFieldExpressionFactory
        // because the field expression factory is supposed to map 'thingId' to '_id', which is only meaningful for MongoDB
        final Map<String, String> mappings = new HashMap<>(6);
        mappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        mappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
        addMapping(mappings, Thing.JsonFields.POLICY_ID); // also present as top-level field in search collection, however not indexed
        addMapping(mappings, Thing.JsonFields.REVISION); // also present as top-level field in search collection, however not indexed
        addMapping(mappings, Thing.JsonFields.MODIFIED);
        addMapping(mappings, Thing.JsonFields.CREATED);
        addMapping(mappings, Thing.JsonFields.DEFINITION);
        return ThingsFieldExpressionFactory.of(mappings);
    }

    private ActorRef initializeSearchActor(final LimitsConfig limitsConfig,
            final ThingsSearchPersistence thingsSearchPersistence) {

        final var queryParser = getQueryParser(limitsConfig, getContext().getSystem());
        return startChildActor(SearchActor.ACTOR_NAME, SearchActor.props(queryParser, thingsSearchPersistence));
    }

}
