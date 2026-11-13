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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.eclipse.ditto.base.service.RootChildActorStarter;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.pekko.streaming.TimestampPersistence;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.query.QueryParser;
import org.eclipse.ditto.thingsearch.service.persistence.query.validation.QueryCriteriaValidator;
import org.eclipse.ditto.thingsearch.service.updater.actors.SearchUpdaterRootActor;

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

        final var searchPersistenceProvider = SearchPersistenceProvider.get(actorSystem,
                ScopedConfig.dittoExtension(actorSystem.settings().config()));
        log.info("Resolved thing-search persistence provider <{}> via config key " +
                        "<ditto.extensions.search-persistence-provider>",
                searchPersistenceProvider.getClass().getName());

        RootChildActorStarter.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()))
                .execute(getContext());

        // bootstrap the backend search schema/indices before any persistence is used (mirrors ThingsRootActor's
        // PersistenceBackendProvider.bootstrapSchema() call order). On PostgreSQL it waits a bounded, configured time
        // while the database is not reachable yet and throws otherwise.
        searchPersistenceProvider.bootstrapSchema();

        final ThingsSearchPersistence thingsSearchPersistence = searchPersistenceProvider.createSearchPersistence();
        final ActorRef searchActor = initializeSearchActor(searchConfig, thingsSearchPersistence, pubSubMediator);
        pubSubMediator.tell(DistPubSubAccess.put(searchActor), getSelf());

        final TimestampPersistence backgroundSyncPersistence =
                searchPersistenceProvider.createBackgroundSyncBookmarkPersistence();

        final ActorRef searchUpdaterRootActor = startChildActor(SearchUpdaterRootActor.ACTOR_NAME,
                SearchUpdaterRootActor.props(searchConfig, searchActor, pubSubMediator, thingsSearchPersistence,
                        backgroundSyncPersistence));
        final ActorRef healthCheckingActor =
                initializeHealthCheckActor(searchConfig, searchUpdaterRootActor, searchPersistenceProvider);

        bindHttpStatusRoute(searchConfig.getHttpConfig(), healthCheckingActor);
    }

    static QueryParser getQueryParser(final SearchConfig searchConfig, final ActorSystem actorSystem) {
        final var limitsConfig = searchConfig.getLimitsConfig();
        final var fieldExpressionFactory = getThingsFieldExpressionFactory(searchConfig);
        final var searchPersistenceProvider = SearchPersistenceProvider.get(actorSystem,
                ScopedConfig.dittoExtension(actorSystem.settings().config()));
        final QueryBuilderFactory queryBuilderFactory = searchPersistenceProvider.queryBuilderFactory(limitsConfig);
        final var queryCriteriaValidator =
                QueryCriteriaValidator.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config()));
        return QueryParser.of(fieldExpressionFactory, queryBuilderFactory, queryCriteriaValidator);
    }

    private ActorRef initializeHealthCheckActor(final SearchConfig searchConfig,
            final ActorRef searchUpdaterRootActor, final SearchPersistenceProvider searchPersistenceProvider) {
        return startChildActor(SearchHealthCheckingActorFactory.ACTOR_NAME,
                SearchHealthCheckingActorFactory.props(searchConfig, searchUpdaterRootActor, searchPersistenceProvider));
    }

    /**
     * Creates Pekko configuration object Props for this SearchRootActor.
     *
     * @param searchConfig the configuration settings of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Pekko configuration Props object.
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
        final var slowQueryLogConfig = searchConfig.getSlowQueryLogConfig();
        final var props = SearchActor.props(queryParser, thingsSearchPersistence, pubSubMediator, slowQueryLogConfig);
        return startChildActor(SearchActor.ACTOR_NAME, props);
    }

}
