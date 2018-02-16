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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.exists;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceReportResult;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceResultEntry;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.common.model.ResultListImpl;
import org.eclipse.ditto.services.thingsearch.persistence.Indices;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQuery;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.indices.IndexInitializer;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayQueryTimeExceededException;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.client.model.CountOptions;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.MongoCollection;

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

    private final ActorMaterializer materializer;
    private final IndexInitializer indexInitializer;
    private final Duration maxQueryTime;

    /**
     * Initializes the things search persistence with a passed in {@code persistence}.
     *
     * @param clientWrapper the mongoDB persistence wrapper.
     * @param actorSystem the Akka ActorSystem.
     */
    public MongoThingsSearchPersistence(final MongoClientWrapper clientWrapper, final ActorSystem actorSystem) {
        collection = clientWrapper.getDatabase().getCollection(PersistenceConstants.THINGS_COLLECTION_NAME);
        log = Logging.getLogger(actorSystem, getClass());
        materializer = ActorMaterializer.create(actorSystem);
        indexInitializer = IndexInitializer.of(clientWrapper.getDatabase(), materializer);
        maxQueryTime = MongoConfig.getMaxQueryTime(actorSystem.settings().config());
    }

    @Override
    public CompletionStage<Void> initializeIndices() {
        return indexInitializer.initialize(PersistenceConstants.THINGS_COLLECTION_NAME, Indices.Things.all());
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
                    final String namespace = (document.get(PersistenceConstants.FIELD_ID) != null)
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
    public Source<Long, NotUsed> count(final PolicyRestrictedSearchAggregation policyRestrictedSearchAggregation) {
        checkNotNull(policyRestrictedSearchAggregation, "policy restricted aggregation");

        final Source<Document, NotUsed> source = policyRestrictedSearchAggregation.execute(collection, maxQueryTime);

        return source.map(doc -> doc.get(PersistenceConstants.COUNT_RESULT_NAME))
                .map(countResult -> (Number) countResult)
                .map(Number::longValue) // use Number.longValue() to support both Integer and Long values
                .orElse(Source.single(0L))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("count");
    }

    @Override
    public Source<ResultList<String>, NotUsed> findAll(final PolicyRestrictedSearchAggregation aggregation) {
        checkNotNull(aggregation, "aggregation");

        final Source<Document, NotUsed> source = aggregation.execute(collection, maxQueryTime);

        return source.map(doc -> doc.getString(PersistenceConstants.FIELD_ID))
                .fold(new ArrayList<String>(), (list, id) -> {
                    list.add(id);
                    return list;
                })
                .map(resultsPlus0ne -> toResultList(resultsPlus0ne, aggregation.getSkip(), aggregation.getLimit()))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("findAll");
    }

    @Override
    public Source<Long, NotUsed> count(final Query query) {
        checkNotNull(query, "query");

        final BsonDocument queryFilter = getMongoFilter(query);
        log.debug("count with query filter <{}>.", queryFilter);

        final Bson filter = and(exists(PersistenceConstants.FIELD_DELETED, false), queryFilter);

        final CountOptions countOptions = new CountOptions()
                .skip(query.getSkip())
                .limit(query.getLimit())
                .maxTime(maxQueryTime.getSeconds(), TimeUnit.SECONDS);

        return Source.fromPublisher(collection.count(filter, countOptions))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("count");
    }

    @Override
    public Source<ResultList<String>, NotUsed> findAll(final Query query) {
        checkNotNull(query, "query");

        final BsonDocument queryFilter = getMongoFilter(query);
        if (log.isDebugEnabled()) {
            log.debug("findAll with query filter <{}>.", queryFilter);
        }

        final Bson filter = and(exists(PersistenceConstants.FIELD_DELETED, false), queryFilter);
        final Optional<Bson> sortOptions = Optional.of(getMongoSort(query));

        final int limit = query.getLimit();
        final int skip = query.getSkip();
        final Bson projection = new Document(PersistenceConstants.FIELD_ID, 1);

        return Source.fromPublisher(collection.find(filter, Document.class)
                .sort(sortOptions.orElse(null))
                .limit(limit + 1)
                .skip(skip)
                .projection(projection)
                .maxTime(maxQueryTime.getSeconds(), TimeUnit.SECONDS)
        )
                .map(doc -> doc.getString(PersistenceConstants.FIELD_ID))
                .fold(new ArrayList<String>(), (list, id) -> {
                    list.add(id);
                    return list;
                })
                .map(resultsPlus0ne -> toResultList(resultsPlus0ne, skip, limit))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("findAll");
    }

    private ResultList<String> toResultList(final List<String> resultsPlus0ne, final int skip, final int limit) {
        log.debug("Creating paged ResultList from parameters: resultsPlusOne=<{}>,skip={},limit={}",
                resultsPlus0ne, skip, limit);

        final ResultList<String> pagedResultList;
        if (resultsPlus0ne.size() <= limit) {
            pagedResultList = new ResultListImpl<>(resultsPlus0ne, ResultList.NO_NEXT_PAGE);
        } else {
            // MongoDB returned limit + 1 items. However only <limit> items are of interest per page.
            resultsPlus0ne.remove(limit);
            final long nextPageOffset = (long) skip + limit;
            pagedResultList = new ResultListImpl<>(resultsPlus0ne, nextPageOffset);
        }

        log.debug("Returning paged ResultList: {}", pagedResultList);
        return pagedResultList;
    }

    private static BsonDocument getMongoFilter(final Query query) {
        return org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.toBsonDocument(
                CreateBsonVisitor.apply(query.getCriteria()));
    }

    private static Bson getMongoSort(final Query query) {
        final MongoQuery mongoQuery = (MongoQuery) query;
        return mongoQuery.getSortOptionsAsBson();
    }

    private PartialFunction<Throwable, Throwable> handleMongoExecutionTimeExceededException() {
        return new PFBuilder<Throwable, Throwable>()
                .match(Throwable.class, error ->
                        error instanceof MongoExecutionTimeoutException
                                ? GatewayQueryTimeExceededException.newBuilder().build()
                                : error
                )
                .build();
    }
}
