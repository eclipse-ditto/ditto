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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.BACKGROUND_SYNC_COLLECTION_NAME;

import java.io.ObjectInputFilter;
import java.util.HashMap;
import java.util.Map;

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
import org.eclipse.ditto.services.base.actors.DittoRootActor;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.thingsearch.common.config.SearchConfig;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryParser;
import org.eclipse.ditto.services.thingsearch.persistence.query.validation.QueryCriteriaValidator;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoTimestampPersistence;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;

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

    private static final String KAMON_METRICS_PREFIX = "search";

    private final ActorRef pubSubMediator;

    private final LoggingAdapter log;

    @SuppressWarnings("unused")
    private SearchRootActor(final SearchConfig searchConfig, final ActorRef pubSubMediator) {

        this.pubSubMediator = pubSubMediator;
        log = Logging.getLogger(getContext().system(), this);

        final MongoDbConfig mongoDbConfig = searchConfig.getMongoDbConfig();
        final MongoDbConfig.MonitoringConfig monitoringConfig = mongoDbConfig.getMonitoringConfig();

        final DittoMongoClient mongoDbClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                .addCommandListener(getCommandListenerOrNull(monitoringConfig))
                .addConnectionPoolListener(getConnectionPoolListenerOrNull(monitoringConfig))
                .build();

        final ThingsSearchPersistence thingsSearchPersistence = getThingsSearchPersistence(searchConfig, mongoDbClient);
        final ActorRef searchActor = initializeSearchActor(searchConfig.getLimitsConfig(), thingsSearchPersistence);
        pubSubMediator.tell(DistPubSubAccess.put(searchActor), getSelf());

        final ActorSystem actorSystem = getContext().getSystem();
        final TimestampPersistence backgroundSyncPersistence =
                MongoTimestampPersistence.initializedInstance(BACKGROUND_SYNC_COLLECTION_NAME, mongoDbClient,
                        SystemMaterializer.get(actorSystem).materializer());

        final ActorRef searchUpdaterRootActor = startChildActor(SearchUpdaterRootActor.ACTOR_NAME,
                SearchUpdaterRootActor.props(searchConfig, pubSubMediator, thingsSearchPersistence,
                        backgroundSyncPersistence));
        final ActorRef healthCheckingActor = initializeHealthCheckActor(searchConfig, searchUpdaterRootActor);

        bindHttpStatusRoute(searchConfig.getHttpConfig(), healthCheckingActor);
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

        final QueryParser queryParser = getQueryParser(limitsConfig, getContext().getSystem(), pubSubMediator);

        return startChildActor(SearchActor.ACTOR_NAME, SearchActor.props(queryParser, thingsSearchPersistence));
    }

    protected static QueryParser getQueryParser(final LimitsConfig limitsConfig, final ActorSystem actorSystem, ActorRef pubSubMediator) {
        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory = getThingsFieldExpressionFactory();
        final QueryBuilderFactory queryBuilderFactory = new MongoQueryBuilderFactory(limitsConfig);
        final QueryCriteriaValidator queryCriteriaValidator = QueryCriteriaValidator.get(actorSystem, pubSubMediator);
        return QueryParser.of(criteriaFactory, fieldExpressionFactory, queryBuilderFactory, queryCriteriaValidator);
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
        addMapping(mappings, Thing.JsonFields.POLICY_ID);
        addMapping(mappings, Thing.JsonFields.REVISION);
        addMapping(mappings, Thing.JsonFields.MODIFIED);
        addMapping(mappings, Thing.JsonFields.CREATED);
        addMapping(mappings, Thing.JsonFields.DEFINITION);
        return new ThingsFieldExpressionFactoryImpl(mappings);
    }

    private static void addMapping(final Map<String, String> fieldMappings, final JsonFieldDefinition<?> definition) {
        final JsonPointer pointer = definition.getPointer();
        final String key = pointer.getRoot().map(JsonKey::toString).orElse("");
        final String value = pointer.toString();
        fieldMappings.put(key, value);
    }

}
