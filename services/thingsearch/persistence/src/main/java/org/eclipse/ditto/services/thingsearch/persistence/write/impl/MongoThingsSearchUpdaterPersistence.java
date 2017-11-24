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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.LAST_SUCCESSFUL_SYNC_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.SET;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.UNSET;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.MongoClientWrapper;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.DocumentMapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.reactivestreams.Publisher;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.NotUsed;
import akka.event.LoggingAdapter;
import akka.japi.function.Function;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * MongoDB specific implementation of the {@link ThingsSearchUpdaterPersistence}.
 */
public final class MongoThingsSearchUpdaterPersistence extends AbstractThingsSearchUpdaterPersistence {

    private static final int MONGO_DUPLICATE_KEY_ERROR_CODE = 11000;
    private static final int MONGO_INDEX_VALUE_ERROR_CODE = 17280;
    private final MongoCollection<Document> collection;
    private final MongoCollection<Document> policiesCollection;
    private final MongoCollection<Document> lastSuccessfulSearchSyncCollection;

    /**
     * Constructor.
     *
     * @param clientWrapper the client wrapper holding the connection information.
     * @param log the logger to use for logging.
     */
    public MongoThingsSearchUpdaterPersistence(final MongoClientWrapper clientWrapper, final LoggingAdapter log) {
        super(log);
        collection = clientWrapper.getDatabase().getCollection(THINGS_COLLECTION_NAME);
        policiesCollection = clientWrapper.getDatabase().getCollection(POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME);
        // todo: nowehere documented what createCollection will return if the collection already exists
//        clientWrapper.getDatabase().createCollection(LAST_SUCCESSFUL_SYNC_COLLECTION_NAME, new
//                // sizeinbytes needed?
//                CreateCollectionOptions().capped(true).sizeInBytes(1024L).maxDocuments(1L));
        lastSuccessfulSearchSyncCollection = clientWrapper.getDatabase().getCollection(
                LAST_SUCCESSFUL_SYNC_COLLECTION_NAME);
    }

    private static List<UpdateOneModel<Document>> createWriteModels(final Bson filter,
            final CombinedThingWrites combinedThingWrites) {
        return combinedThingWrites.getCombinedWriteDocuments().stream()
                .map(update -> new UpdateOneModel<Document>(filter, update, new UpdateOptions().upsert(true)))
                .collect(Collectors.toList());
    }

    private static Bson filterWithExactRevision(final String thingId, final long revision) {
        return and(eq(FIELD_ID, thingId), eq(FIELD_REVISION, revision));
    }

    private static Bson filterWithEqualThingId(final String thingId) {
        return eq(FIELD_ID, thingId);
    }

    private static Bson filterWithLowerRevision(final String thingId, final long revision) {
        return and(eq(FIELD_ID, thingId), lt(FIELD_REVISION, revision));
    }

    private static Document toUpdate(final Document document, final long thingRevision, final long policyRevision) {
        document.put(FIELD_REVISION, thingRevision);
        document.put(FIELD_POLICY_REVISION, policyRevision);
        return toUpdate(document);
    }

    private static Document toUpdate(final Document document) {
        return new Document().append(SET, document).append(UNSET, new Document(FIELD_DELETED, 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final Source<Boolean, NotUsed> save(final Thing thing, final long revision, final long policyRevision) {
        final Bson filter = filterWithLowerRevision(getThingId(thing), revision);
        final Document document = toUpdate(DocumentMapper.toDocument(thing), revision, policyRevision);
        return Source.fromPublisher(collection.updateOne(filter, document, new UpdateOptions().upsert(true)))
                .map(updateResult -> updateResult.getMatchedCount() > 0 || null != updateResult.getUpsertedId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final PartialFunction<Throwable, Source<Boolean, NotUsed>> errorRecovery(final String thingId) {
        return new PFBuilder<Throwable, Source<Boolean, NotUsed>>()
                .matchAny(throwable -> {
                    final String msgTemplate = "Update operation for <{}> failed due to";
                    if (isErrorOfType(MONGO_DUPLICATE_KEY_ERROR_CODE, throwable)) {
                        // expected error - thus as DEBUG:
                        log.debug(msgTemplate + " a duplicate key: {}", thingId, throwable.getMessage());
                        return Source.single(Boolean.FALSE);
                    } else if (isErrorOfType(MONGO_INDEX_VALUE_ERROR_CODE, throwable)) {
                        log.error(throwable, msgTemplate + " a too large value which cannot be indexed!", thingId);
                        return Source.single(Boolean.TRUE);
                    } else {
                        log.error(throwable, msgTemplate + " an unexpected error: {}", thingId, throwable.getMessage());
                        return Source.failed(throwable);
                    }
                })
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<Boolean, NotUsed> delete(final String thingId) {
        final Bson filter = eq(FIELD_ID, thingId);
        final Bson document = new Document(SET, new Document(FIELD_DELETED, new Date()));
        return delete(thingId, filter, document);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<Boolean, NotUsed> delete(final String thingId, final long revision) {
        final Bson filter = filterWithLowerRevision(thingId, revision);
        final Document delete = new Document()
                .append(FIELD_DELETED, new Date())
                .append(FIELD_REVISION, revision);
        final Bson document = new Document(SET, delete);
        return delete(thingId, filter, document);
    }

    private Source<Boolean, NotUsed> delete(final String thingId, final Bson filter, final Bson document) {
        return Source.fromPublisher(collection.updateOne(filter, document))
                .flatMapConcat(deleteResult -> {
                    if (deleteResult.getMatchedCount() <= 0) {
                        return Source.single(Boolean.FALSE);
                    }
                    final PolicyUpdate deletePolicyEntries = PolicyUpdateFactory.createDeleteThingUpdate(thingId);
                    final Bson policyIndexRemoveFilter = deletePolicyEntries.getPolicyIndexRemoveFilter();
                    return Source.fromPublisher(policiesCollection.deleteMany(policyIndexRemoveFilter))
                            .map(r -> true);
                });
    }

    /**
     * /** Executes the passed in {@link CombinedThingWrites} using a bulk write operation on MongoDB. The bulk write is
     * performed with the ordered options which means that if one write fails, the other writes will not be executed.
     *
     * @param thingId the id of the thing to update.
     * @param combinedThingWrites the combined thing writes to execute in this update.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    @Override
    public final Source<Boolean, NotUsed> executeCombinedWrites(final String thingId,
            final CombinedThingWrites combinedThingWrites) {
        final Bson filter = filterWithExactRevision(thingId, combinedThingWrites.getSourceSequenceNumber());
        final List<UpdateOneModel<Document>> writeModels = createWriteModels(filter, combinedThingWrites);
        addPolicyRelatedThingWrites(filter, writeModels, combinedThingWrites.getCombinedPolicyUpdates());
        writeModels.add(new UpdateOneModel<>(filter, combinedThingWrites.getSequenceNumberUpdate()));

        final BulkWriteOptions writeOrdered = new BulkWriteOptions();
        writeOrdered.ordered(true);

        return Source.fromPublisher(collection.bulkWrite(writeModels, writeOrdered))
                .flatMapConcat(mapCombinedWritesResult(combinedThingWrites))
                .recoverWithRetries(1, errorRecovery(thingId));
    }

    private Function<BulkWriteResult, Source<Boolean, NotUsed>> mapCombinedWritesResult(
            final CombinedThingWrites combinedThingWrites) {
        final List<WriteModel<Document>> policyWriteModels = createPolicyWriteModels(combinedThingWrites
                .getCombinedPolicyUpdates());
        final BulkWriteOptions writeOrdered = new BulkWriteOptions();
        writeOrdered.ordered(true);
        return r -> {
            if (r.getModifiedCount() > 0 || r.getInsertedCount() > 0) {
                if (!policyWriteModels.isEmpty()) {
                    return Source.fromPublisher(policiesCollection.bulkWrite(policyWriteModels, writeOrdered))
                            .map(r2 -> Boolean.TRUE);
                } else {
                    return Source.single(Boolean.TRUE);
                }
            } else {
                return Source.single(Boolean.FALSE);
            }
        };
    }

    private static void addPolicyRelatedThingWrites(final Bson filter,
            final Collection<UpdateOneModel<Document>> writeModels,
            final Iterable<PolicyUpdate> combinedPolicyUpdates) {

        combinedPolicyUpdates.forEach(policyUpdate -> {
            if (policyUpdate.getPullGlobalReads() != null) {
                writeModels.add(new UpdateOneModel<>(filter, policyUpdate.getPullGlobalReads()));
            }
            if (policyUpdate.getPushGlobalReads() != null) {
                writeModels.add(new UpdateOneModel<>(filter, policyUpdate.getPushGlobalReads()));
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<Boolean, NotUsed> updatePolicy(final Thing thing, final PolicyEnforcer policyEnforcer) {
        if (thing == null || policyEnforcer == null) {
            log.error("Thing or policyEnforcer was null when trying to update policy search index. Thing: <{}>, " +
                    "PolicyEnforcer: <{}>", thing, policyEnforcer);
            return Source.single(Boolean.FALSE);
        }

        final PolicyUpdate policyUpdate = PolicyUpdateFactory.createPolicyIndexUpdate(thing, policyEnforcer);
        return Source.fromPublisher(updatePolicy(thing, policyUpdate))
                .flatMapConcat(mapPolicyUpdateResult(policyUpdate))
                .recoverWithRetries(1, errorRecovery(getThingId(thing)));
    }

    private Publisher<BulkWriteResult> updatePolicy(final Thing thing, final PolicyUpdate policyUpdate) {
        final Bson filter = filterWithEqualThingId(getThingId(thing));
        final List<UpdateOneModel<Document>> writeThingIndexModels = createThingIndexModels(filter, policyUpdate);
        final BulkWriteOptions writeOrdered = new BulkWriteOptions();
        writeOrdered.ordered(true);
        return collection.bulkWrite(writeThingIndexModels, writeOrdered);
    }

    private Function<BulkWriteResult, Source<Boolean, NotUsed>> mapPolicyUpdateResult(final PolicyUpdate policyUpdate) {
        final BulkWriteOptions writeOrdered = new BulkWriteOptions();
        writeOrdered.ordered(true);
        return result -> {
            if (result.getMatchedCount() > 0) {
                final List<WriteModel<Document>> writePolicyIndexModels =
                        createPolicyIndexModels(policyUpdate.getPolicyIndexRemoveFilter(),
                                policyUpdate.getPolicyIndexInsertEntries());
                return Source.fromPublisher(
                        policiesCollection.bulkWrite(writePolicyIndexModels, writeOrdered))
                        .map(result2 -> Boolean.TRUE);
            } else {
                return Source.single(Boolean.FALSE);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<Set<String>, NotUsed> getThingIdsForPolicy(final String policyId) {
        final Bson filter = eq(FIELD_POLICY_ID, policyId);
        return Source.fromPublisher(collection.find(filter)
                .projection(new BsonDocument(FIELD_ID, new BsonInt32(1))))
                .map(doc -> doc.getString(FIELD_ID))
                .fold(new HashSet<>(), (set, id) -> {
                    set.add(id);
                    return set;
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<ThingMetadata, NotUsed> getThingMetadata(final String thingId) {
        final Bson filter = eq(FIELD_ID, thingId);
        return Source.fromPublisher(collection.find(filter)
                .projection(Projections.include(FIELD_REVISION, FIELD_POLICY_ID, FIELD_POLICY_REVISION)))
                .map(mapThingMetadataToModel())
                .orElse(defaultThingMetadata());
    }

    /**
     * todo: test
     * <p>
     * {@inheritDoc}
     */
    @Override
    public Source<Boolean, NotUsed> updateLastSuccessfulSyncTimestamp(final LocalDateTime utcTimestamp) {
        if (null == utcTimestamp) {
            log.error("Timestamp was null when trying to update the last successful sync timestamp");
            return Source.single(Boolean.FALSE);
        }

        final Date mongoStorableDate = Date.from(utcTimestamp.toInstant(ZoneOffset.UTC));

        final Bson filter = eq(FIELD_ID, "search");
        final Document toStore = new Document()
                .append(FIELD_ID, "search")
                .append("date", mongoStorableDate);
        // todo: need to handle errors here?
        return Source.fromPublisher(lastSuccessfulSearchSyncCollection.findOneAndReplace(filter, toStore))
                .map(document -> {
                    if (null == document) {
                        log.debug("Successfully inserted first timestamp for search synchronization: <{}>",
                                utcTimestamp.toString());
                    } else {
                        log.debug("Replaced last synchronization timestamp document <{}> with <{}>.",
                                document.toJson(), utcTimestamp.toString());
                    }
                    return Boolean.TRUE;
                });
    }

    /**
     * {@inheritDoc}
     */
    // TODO: test
    @Override
    public Source<LocalDateTime, NotUsed> retrieveLastSuccessfulSyncTimestamp() {
        final Bson filter = eq(FIELD_ID, "search");
        return Source.fromPublisher(lastSuccessfulSearchSyncCollection.find(filter))
                .map(document -> {
                    if (document.containsKey("search")) {
                        return LocalDateTime.from(document.getDate("search").toInstant());
                    } else {
                        return LocalDateTime.from(new Date(0L).toInstant());

                    }
                });
    }

    private Source<ThingMetadata, NotUsed> defaultThingMetadata() {
        return Source.single(new ThingMetadata(-1L, null, -1L));
    }

    private Function<Document, ThingMetadata> mapThingMetadataToModel() {
        return doc -> new ThingMetadata(
                doc.containsKey(FIELD_REVISION) ? doc.getLong(FIELD_REVISION) : -1L,
                doc.containsKey(FIELD_POLICY_ID) ? doc.getString(FIELD_POLICY_ID) : null,
                doc.containsKey(FIELD_POLICY_REVISION) ? doc.getLong(FIELD_POLICY_REVISION) : -1L);
    }

    private static List<WriteModel<Document>> createPolicyIndexModels(final Bson policiesFilter,
            final Collection<Document> policyEntries) {

        final DeleteManyModel<Document> deleteExistingPolicyEntries = new DeleteManyModel<>(policiesFilter);
        final List<WriteModel<Document>> writeModels = new ArrayList<>();
        writeModels.add(deleteExistingPolicyEntries);
        policyEntries.stream()
                .map(InsertOneModel::new)
                .forEach(writeModels::add);
        return writeModels;
    }

    private static List<WriteModel<Document>> createPolicyWriteModels(
            final Iterable<PolicyUpdate> combinedPolicyUpdates) {

        final List<WriteModel<Document>> writeModels = new ArrayList<>();
        combinedPolicyUpdates.forEach(policyUpdate -> {
            writeModels.add(new DeleteManyModel<>(policyUpdate.getPolicyIndexRemoveFilter()));
            policyUpdate.getPolicyIndexInsertEntries()
                    .forEach(document -> writeModels.add(new InsertOneModel<>(document)));
        });
        return writeModels;
    }

    private static List<UpdateOneModel<Document>> createThingIndexModels(final Bson filter, final PolicyUpdate update) {
        final UpdateOneModel<Document> removeOldEntries = new UpdateOneModel<>(filter, update.getPullGlobalReads());
        final UpdateOneModel<Document> addNewEntries = new UpdateOneModel<>(filter, update.getPushGlobalReads());
        final UpdateOneModel<Document> removeAclEntries = new UpdateOneModel<>(filter, update.getPullAclEntries());

        return Arrays.asList(removeOldEntries, addNewEntries, removeAclEntries);
    }

    private static boolean isErrorOfType(final int errorCode, final Object o) {
        if (o instanceof MongoWriteException) {
            final MongoWriteException mongoWriteException = (MongoWriteException) o;

            return hasErrorCode(mongoWriteException.getError(), errorCode);
        } else if (o instanceof MongoBulkWriteException) {
            final MongoBulkWriteException mongoWriteException = (MongoBulkWriteException) o;

            return hasErrorCode(mongoWriteException.getWriteErrors().iterator().next(), errorCode);
        } else {
            return false;
        }
    }

    private static boolean hasErrorCode(final WriteError error, final int expectedErrorCode) {
        return expectedErrorCode == error.getCode();
    }

}
