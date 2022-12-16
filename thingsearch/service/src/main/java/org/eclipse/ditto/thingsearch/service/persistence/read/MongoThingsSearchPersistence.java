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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.internal.utils.persistence.mongo.BsonUtil;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexInitializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.QueryTimeExceededException;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceResultEntry;
import org.eclipse.ditto.thingsearch.service.common.config.SearchPersistenceConfig;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.ResultListImpl;
import org.eclipse.ditto.thingsearch.service.common.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.service.persistence.Indices;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors.GetSortBsonVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQuery;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.mongodb.scala.MongoClient;
import org.reactivestreams.Publisher;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.PFBuilder;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Persistence Service Implementation for asynchronous MongoDB search.
 */
public final class MongoThingsSearchPersistence implements ThingsSearchPersistence {

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
    public MongoThingsSearchPersistence(final DittoMongoClient mongoClient, final ActorSystem actorSystem,
            final SearchPersistenceConfig persistenceConfig) {
        final MongoDatabase database = mongoClient.getDefaultDatabase();
        final var readConcern = persistenceConfig.readConcern();
        final var readPreference = persistenceConfig.readPreference().getMongoReadPreference();
        collection = database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME)
                .withReadConcern(readConcern.getMongoReadConcern())
                .withReadPreference(readPreference);
        log = Logging.getLogger(actorSystem, getClass());
        indexInitializer = IndexInitializer.of(database, SystemMaterializer.get(actorSystem).materializer());
        maxQueryTime = mongoClient.getDittoSettings().getMaxQueryTime();
        hints = MongoHints.empty();
        log.info("Query readConcern=<{}> readPreference=<{}>", readConcern, readPreference);
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
        final MongoHints theHints = MongoHints.byNamespace(jsonString);
        return new MongoThingsSearchPersistence(collection, log, indexInitializer, maxQueryTime, theHints);
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

        return Source.fromPublisher(collection.countDocuments(queryFilter, countOptions))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("count");
    }

    @Override
    public Source<Long, NotUsed> sudoCount(final Query query) {
        return count(query, null);
    }

    @Override
    public Source<ResultList<TimestampedThingId>, NotUsed> findAll(final Query query,
            @Nullable final List<String> authorizationSubjectIds,
            @Nullable final Set<String> namespaces) {

        final int skip = query.getSkip();
        final int limit = query.getLimit();
        final int limitPlusOne = limit + 1;

        return findAllInternal(query, authorizationSubjectIds, namespaces, limitPlusOne, maxQueryTime)
                .grouped(limitPlusOne)
                .orElse(Source.single(Collections.emptyList()))
                .map(resultsPlus0ne -> toResultList(resultsPlus0ne, skip, limit, query.getSortOptions()))
                .mapError(handleMongoExecutionTimeExceededException())
                .log("findAll");
    }

    @Override
    public Source<ThingId, NotUsed> findAllUnlimited(final Query query, final List<String> authorizationSubjectIds,
            @Nullable final Set<String> namespaces) {

        final Integer limit = query.getLimit() == Integer.MAX_VALUE ? null : query.getLimit();
        return findAllInternal(query, authorizationSubjectIds, namespaces, limit, null)
                .map(MongoThingsSearchPersistence::toThingId)
                .idleTimeout(maxQueryTime);
    }

    /**
     * Recover a write model from the persistence.
     *
     * @param thingId the thing ID.
     * @return the last write model if the thing exists in the search index, or a {@code ThingDeleteModel} if the thing
     * does not exist.
     */
    public Source<AbstractWriteModel, NotUsed> recoverLastWriteModel(final ThingId thingId) {
        final var metadata = Metadata.ofDeleted(thingId);
        final var publisher =
                collection.find(Filters.eq(PersistenceConstants.FIELD_ID, thingId.toString())).limit(1);
        final var emptySource =
                Source.<AbstractWriteModel>single(ThingDeleteModel.of(metadata));
        return Source.fromPublisher(publisher)
                .map(MongoThingsSearchPersistence::documentToWriteModel)
                .orElse(emptySource);
    }

    private Source<Document, NotUsed> findAllInternal(final Query query, final List<String> authorizationSubjectIds,
            @Nullable final Set<String> namespaces,
            @Nullable final Integer limit,
            @Nullable final Duration maxQueryTime) {

        checkNotNull(query, "query");

        final BsonDocument queryFilter = getMongoFilter(query, authorizationSubjectIds);
        if (log.isDebugEnabled()) {
            log.debug("findAll with query filter <{}>.", queryFilter);
        }

        final Bson sortOptions = getMongoSort(query);

        final int skip = query.getSkip();
        final Bson projection = GetSortBsonVisitor.projections(query.getSortOptions());
        final FindPublisher<Document> findPublisher =
                collection.find(queryFilter, Document.class)
                        .hint(hints.getHint(namespaces).orElse(null))
                        .sort(sortOptions)
                        .skip(skip)
                        .projection(projection);

        final FindPublisher<Document> findPublisherWithLimit;
        if (null != limit) {
            findPublisherWithLimit = findPublisher
                    .limit(limit)
                    .batchSize(limit);
        } else {
            findPublisherWithLimit = findPublisher;
        }
        final FindPublisher<Document> findPublisherWithMaxQueryTime = maxQueryTime != null
                ? findPublisherWithLimit.maxTime(maxQueryTime.getSeconds(), TimeUnit.SECONDS)
                : findPublisherWithLimit;

        return Source.fromPublisher(findPublisherWithMaxQueryTime);
    }

    @Override
    public Source<Metadata, NotUsed> sudoStreamMetadata(final EntityId lowerBound) {
        final Bson notDeletedFilter = Filters.exists(PersistenceConstants.FIELD_DELETE_AT, false);
        final Bson filter = LowerBound.emptyEntityId(lowerBound.getEntityType()).equals(lowerBound)
                ? notDeletedFilter
                : Filters.and(notDeletedFilter, Filters.gt(PersistenceConstants.FIELD_ID, lowerBound.toString()));
        final Bson relevantFieldsProjection =
                Projections.include(PersistenceConstants.FIELD_ID, PersistenceConstants.FIELD_REVISION,
                        PersistenceConstants.FIELD_POLICY_ID, PersistenceConstants.FIELD_POLICY_REVISION,
                        PersistenceConstants.FIELD_PATH_MODIFIED);
        final Bson sortById = Sorts.ascending(PersistenceConstants.FIELD_ID);
        final Publisher<Document> publisher = collection.find(filter)
                .projection(relevantFieldsProjection)
                .sort(sortById);
        return Source.fromPublisher(publisher).map(MongoThingsSearchPersistence::readAsMetadata);
    }

    private ResultList<TimestampedThingId> toResultList(final List<Document> resultsPlus0ne, final int skip,
            final int limit,
            final List<SortOption> sortOptions) {

        log.debug("Creating paged ResultList from parameters: resultsPlusOne=<{}>,skip={},limit={}",
                resultsPlus0ne, skip, limit);

        final ResultList<TimestampedThingId> pagedResultList;
        if (resultsPlus0ne.size() <= limit || limit <= 0) {
            pagedResultList = new ResultListImpl<>(toTimestampedThingIds(resultsPlus0ne), ResultList.NO_NEXT_PAGE);
        } else {
            // MongoDB returned limit + 1 items. However only <limit> items are of interest per page.
            final List<Document> results = resultsPlus0ne.subList(0, limit);
            final Document lastResult = results.get(limit - 1);
            final long nextPageOffset = (long) skip + limit;
            final JsonArray sortValues = GetSortBsonVisitor.sortValuesAsArray(lastResult, sortOptions);
            pagedResultList = new ResultListImpl<>(toTimestampedThingIds(results), nextPageOffset, sortValues);
        }

        log.debug("Returning paged ResultList: {}", pagedResultList);
        return pagedResultList;
    }

    private static List<TimestampedThingId> toTimestampedThingIds(final List<Document> docs) {
        return docs.stream()
                .map(MongoThingsSearchPersistence::toTimestampedThingId)
                .toList();
    }

    private static TimestampedThingId toTimestampedThingId(final Document doc) {
        return new TimestampedThingId(toThingId(doc), getModifiedTimestampOptional(doc));
    }

    private static ThingId toThingId(final Document doc) {
        return ThingId.of(doc.getString(PersistenceConstants.FIELD_ID));
    }

    private static Optional<Instant> getModifiedTimestampOptional(final Document doc) {
        try {
            final var path = List.of(PersistenceConstants.FIELD_THING, PersistenceConstants.FIELD_MODIFIED);
            final var timestampString = doc.getEmbedded(path, String.class);
            if (timestampString != null) {
                return Optional.of(Instant.parse(timestampString));
            }
        } catch (final Exception e) {
            // ignore invalid or nonexistent timestamp
        }
        return Optional.empty();
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
                                ? QueryTimeExceededException.newBuilder().build()
                                : error
                )
                .build();
    }

    private static Metadata readAsMetadata(final Document document) {
        final ThingId thingId = ThingId.of(document.getString(PersistenceConstants.FIELD_ID));
        final long thingRevision =
                Optional.ofNullable(document.getLong(PersistenceConstants.FIELD_REVISION)).orElse(0L);
        final String policyIdInPersistence = document.getString(PersistenceConstants.FIELD_POLICY_ID);
        final PolicyId policyId = policyIdInPersistence.isEmpty() ? null : PolicyId.of(policyIdInPersistence);
        final long policyRevision =
                Optional.ofNullable(document.getLong(PersistenceConstants.FIELD_POLICY_REVISION)).orElse(0L);
        final String nullableTimestamp =
                document.getEmbedded(List.of(PersistenceConstants.FIELD_THING, PersistenceConstants.FIELD_MODIFIED),
                        String.class);
        final Instant modified = Optional.ofNullable(nullableTimestamp).map(Instant::parse).orElse(null);
        final PolicyTag thingPolicyTag =
                Optional.ofNullable(policyId).map(id -> PolicyTag.of(id, policyRevision)).orElse(null);
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final Set<PolicyTag> referencedPolicies =
                Optional.ofNullable(document.getList(PersistenceConstants.FIELD_REFERENCED_POLICIES, Document.class))
                        .orElseGet(List::of)
                        .stream()
                        .map(Bson::toBsonDocument)
                        .map(dittoBsonJson::serialize)
                        .map(PolicyTag::fromJson)
                        .collect(Collectors.toSet());
        return Metadata.of(thingId, thingRevision, thingPolicyTag, referencedPolicies, modified, null);
    }

    private static AbstractWriteModel documentToWriteModel(final Document document) {
        final var bsonDocument = document.toBsonDocument(Document.class, MongoClient.DEFAULT_CODEC_REGISTRY());
        final Metadata actualMetadata = readAsMetadata(document);
        return ThingWriteModel.of(actualMetadata, bsonDocument);
    }
}
