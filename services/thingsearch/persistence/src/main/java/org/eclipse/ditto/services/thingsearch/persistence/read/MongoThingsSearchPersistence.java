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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceReportResult;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceResultEntry;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.common.model.ResultListImpl;
import org.eclipse.ditto.services.thingsearch.persistence.Indices;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQuery;
import org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.indices.IndexInitializer;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayQueryTimeExceededException;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.ReadPreference;
import com.mongodb.client.model.CountOptions;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.PFBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Persistence Service Implementation for asynchronous MongoDB search.
 */
public class MongoThingsSearchPersistence implements ThingsSearchPersistence {

    private final MongoCollection<Document> collection;
    private final LoggingAdapter log;

    private final IndexInitializer indexInitializer;
    private final Duration maxQueryTime;
    private final MongoHints hints;

    /**
     * Initializes the things search persistence with a passed in {@code persistence}.
     *
     * @param mongoClient the mongoDB persistence wrapper.
     * @param actorSystem the Akka ActorSystem.
     */
    public MongoThingsSearchPersistence(final DittoMongoClient mongoClient, final ActorSystem actorSystem) {
        final MongoDatabase database = mongoClient.getDefaultDatabase();
        // configure search persistence to stress the primary as little as possible and tolerate inconsistency
        collection = database
                .getCollection(PersistenceConstants.THINGS_COLLECTION_NAME)
                .withReadPreference(ReadPreference.secondaryPreferred());

        log = Logging.getLogger(actorSystem, getClass());
        final ActorMaterializer materializer = ActorMaterializer.create(actorSystem);
        indexInitializer = IndexInitializer.of(database, materializer);
        maxQueryTime = mongoClient.getDittoSettings().getMaxQueryTime();
        hints = MongoHints.empty();
    }

    private MongoThingsSearchPersistence(
            final MongoCollection<Document> collection,
            final LoggingAdapter log,
            final IndexInitializer indexInitializer,
            final Duration maxQueryTime,
            final MongoHints hints) {

        this.collection = collection;
        this.log = log;
        this.indexInitializer = indexInitializer;
        this.maxQueryTime = maxQueryTime;
        this.hints = hints;
    }

    /**
     * Create a copy of this object with configurable hints for each namespace.
     *
     * @param jsonString JSON representation of hints for queries of each namespace.
     * @return copy of this object with hints configured.
     */
    public MongoThingsSearchPersistence withHintsByNamespace(final String jsonString) {
        final MongoHints hints = MongoHints.byNamespace(jsonString);
        return new MongoThingsSearchPersistence(collection, log, indexInitializer, maxQueryTime, hints);
    }

    @Override
    public CompletionStage<Void> initializeIndices() {
        return indexInitializer.initialize(PersistenceConstants.THINGS_COLLECTION_NAME, Indices.all())
                .exceptionally(t -> {
                    log.error(t, "Index-Initialization failed: {}", t.getMessage());
                    return null;
                });
    }

    @Override
    public Source<SearchNamespaceReportResult, NotUsed> generateNamespaceCountReport() {
        final AggregatePublisher<Document> aggregatePublisher = collection.aggregate(
                Collections.singletonList(
                        new Document("$group",
                                new Document(PersistenceConstants.FIELD_ID, "$_namespace")
                                        .append(PersistenceConstants.FIELD_COUNT, new Document("$sum", 1))
                        )
                )
        );

        return Source.fromPublisher(aggregatePublisher)
                .map(document -> {
                    final String namespace = document.get(PersistenceConstants.FIELD_ID) != null
                            ? document.get(PersistenceConstants.FIELD_ID).toString()
                            : "NOT_MIGRATED";
                    final long count = Long.parseLong(document.get(PersistenceConstants.FIELD_COUNT).toString());
                    return new SearchNamespaceResultEntry(namespace, count);
                })
                .fold(new ArrayList<SearchNamespaceResultEntry>(), (list, entry) -> {
                    list.add(entry);
                    return list;
                })
                .map(SearchNamespaceReportResult::new);
    }

    @Override
    public Source<Long, NotUsed> count(final Query query,
            @Nullable final List<String> authorizationSubjectIds) {

        checkNotNull(query, "query");

        final BsonDocument queryFilter = getMongoFilter(query, authorizationSubjectIds);
        log.debug("count with query filter <{}>.", queryFilter);

        final CountOptions countOptions = new CountOptions()
                .skip(query.getSkip())
                .limit(query.getLimit())
                .maxTime(maxQueryTime.getSeconds(), TimeUnit.SECONDS);

        return Source.fromPublisher(collection.count(queryFilter, countOptions))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("count");
    }

    @Override
    public Source<Long, NotUsed> sudoCount(final Query query) {
        return count(query, null);
    }

    @Override
    public Source<ResultList<String>, NotUsed> findAll(final Query query,
            @Nullable final List<String> authorizationSubjectIds,
            @Nullable final Set<String> namespaces) {

        checkNotNull(query, "query");

        final BsonDocument queryFilter = getMongoFilter(query, authorizationSubjectIds);
        if (log.isDebugEnabled()) {
            log.debug("findAll with query filter <{}>.", queryFilter);
        }

        final Bson sortOptions = getMongoSort(query);

        final int limit = query.getLimit();
        final int skip = query.getSkip();
        final int limitPlusOne = limit + 1;
        final Bson projection = GetSortBsonVisitor.projections(query.getSortOptions());

        return Source.fromPublisher(
                collection.find(queryFilter, Document.class)
                        .hint(hints.getHint(namespaces).orElse(null))
                        .sort(sortOptions)
                        .limit(limitPlusOne)
                        .skip(skip)
                        .projection(projection)
                        .maxTime(maxQueryTime.getSeconds(), TimeUnit.SECONDS))
                .grouped(limitPlusOne)
                .orElse(Source.single(Collections.emptyList()))
                .map(resultsPlus0ne -> toResultList(resultsPlus0ne, skip, limit, query.getSortOptions()))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("findAll");
    }

    private ResultList<String> toResultList(final List<Document> resultsPlus0ne, final int skip, final int limit,
            final List<SortOption> sortOptions) {

        log.debug("Creating paged ResultList from parameters: resultsPlusOne=<{}>,skip={},limit={}",
                resultsPlus0ne, skip, limit);

        final ResultList<String> pagedResultList;
        if (resultsPlus0ne.size() <= limit || limit <= 0) {
            pagedResultList = new ResultListImpl<>(toIds(resultsPlus0ne), ResultList.NO_NEXT_PAGE);
        } else {
            // MongoDB returned limit + 1 items. However only <limit> items are of interest per page.
            final List<Document> results = resultsPlus0ne.subList(0, limit);
            final Document lastResult = results.get(limit - 1);
            final long nextPageOffset = (long) skip + limit;
            final JsonArray sortValues = GetSortBsonVisitor.sortValuesAsArray(lastResult, sortOptions);
            pagedResultList = new ResultListImpl<>(toIds(results), nextPageOffset, sortValues);
        }

        log.debug("Returning paged ResultList: {}", pagedResultList);
        return pagedResultList;
    }

    private static List<String> toIds(final List<Document> docs) {
        return docs.stream()
                .map(doc -> doc.getString(PersistenceConstants.FIELD_ID))
                .collect(Collectors.toList());
    }

    private static BsonDocument getMongoFilter(final Query query,
            @Nullable final List<String> authorizationSubjectIds) {

        if (authorizationSubjectIds != null) {
            return BsonUtil.toBsonDocument(CreateBsonVisitor.apply(query.getCriteria(), authorizationSubjectIds));
        } else {
            return BsonUtil.toBsonDocument(CreateBsonVisitor.sudoApply(query.getCriteria()));
        }
    }

    private static Bson getMongoSort(final Query query) {
        final MongoQuery mongoQuery = (MongoQuery) query;
        return mongoQuery.getSortOptionsAsBson();
    }

    private static PartialFunction<Throwable, Throwable> handleMongoExecutionTimeExceededException() {
        return new PFBuilder<Throwable, Throwable>()
                .match(Throwable.class, error ->
                        error instanceof MongoExecutionTimeoutException
                                ? GatewayQueryTimeExceededException.newBuilder().build()
                                : error
                )
                .build();
    }

}
