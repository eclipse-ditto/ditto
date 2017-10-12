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
package org.eclipse.services.thingsearch.persistence.write.impl;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.SET;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.THINGS_COLLECTION_NAME;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.UNSET;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.services.thingsearch.persistence.MongoClientWrapper;
import org.eclipse.services.thingsearch.persistence.read.document.DocumentMapper;
import org.eclipse.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.NotUsed;
import akka.event.LoggingAdapter;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * MongoDB specific implementation of the {@link ThingsSearchUpdaterPersistence}.
 */
public final class MongoThingsSearchUpdaterPersistence implements ThingsSearchUpdaterPersistence {

    private static final int MONGO_DUPLICATE_KEY_ERROR_CODE = 11000;
    private static final int MONGO_INDEX_VALUE_ERROR_CODE = 17280;
    private final MongoCollection<Document> collection;
    private final MongoCollection<Document> policiesCollection;
    private final LoggingAdapter log;

    /**
     * Constructor.
     *
     * @param clientWrapper the client wrapper holding the connection information.
     * @param log the logger to use for logging.
     */
    public MongoThingsSearchUpdaterPersistence(final MongoClientWrapper clientWrapper, final LoggingAdapter log) {
        collection = clientWrapper.getDatabase().getCollection(THINGS_COLLECTION_NAME);
        policiesCollection = clientWrapper.getDatabase().getCollection(POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME);
        this.log = log;
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

    @Override
    public Source<Boolean, NotUsed> insertOrUpdate(final Thing thing, final long revision, final long policyRevision) {
        final Bson filter = filterWithLowerRevision(getThingId(thing), revision);
        final Document document = toUpdate(DocumentMapper.toDocument(thing), revision, policyRevision);

        return Source
                .fromPublisher(collection.updateOne(filter, document, new UpdateOptions().upsert(true)))
                .map(u -> u.getMatchedCount() > 0 || u.getUpsertedId() != null)
                .recoverWithRetries(1, createErrorRecovery(thing.getId().orElse(null)));
    }

    /**
     * Simulates {@code ThingUpdater.endSyncWithPolicy} from the module {@code search-updater-starter}. For unit tests
     * only.
     *
     * @param thing The thing to write.
     * @param thingRevision The thing's revision.
     * @param policyRevision the revision of the policy to also persist.
     * @param policyEnforcer The enforcer of the thing's policy.
     * @return Result of the sequential writes.
     */
    Source<Boolean, NotUsed> insertOrUpdate(final Thing thing, final long thingRevision, final long policyRevision,
            final PolicyEnforcer policyEnforcer) {
        checkNotNull(policyEnforcer, "policyEnforcer");

        return insertOrUpdate(thing, thingRevision, policyRevision).flatMapConcat(
                u -> updatePolicy(thing, policyEnforcer));
    }

    @Override
    public Source<Boolean, NotUsed> delete(final String thingId, final long revision) {
        final Bson filter = filterWithLowerRevision(thingId, revision);
        final Document delete = new Document()
                .append(FIELD_DELETED, new Date())
                .append(FIELD_REVISION, revision);
        final Bson document = new Document(SET, delete);

        return delete(filter, document, thingId);
    }

    private Source<Boolean, NotUsed> delete(final Bson filter, final Bson document, final String thingId) {
        return Source.fromPublisher(collection.updateOne(filter, document)).flatMapConcat(u -> {
                    if (u.getMatchedCount() <= 0) {
                        return Source.single(Boolean.FALSE);
                    }
                    final PolicyUpdate deletePolicyEntries = PolicyUpdateFactory.createDeleteThingUpdate(thingId);
                    final Bson policyIndexRemoveFilter = deletePolicyEntries.getPolicyIndexRemoveFilter();
                    return Source.fromPublisher(policiesCollection.deleteMany(policyIndexRemoveFilter)).map(r -> true);
                }
        );
    }

    @Override
    public Source<Boolean, NotUsed> delete(final String thingId) {
        final Bson filter = eq(FIELD_ID, thingId);
        final Bson document = new Document(SET, new Document(FIELD_DELETED, new Date()));

        return delete(filter, document, thingId);
    }

    @Override
    public Source<Boolean, NotUsed> executeCombinedWrites(final String thingId,
            final CombinedThingWrites combinedThingWrites) {
        final Bson filter = filterWithExactRevision(thingId, combinedThingWrites.getSourceSequenceNumber());
        final List<UpdateOneModel<Document>> writeModels = createWriteModels(filter, combinedThingWrites);
        addPolicyRelatedThingWrites(filter, writeModels, combinedThingWrites.getCombinedPolicyUpdates());
        writeModels.add(new UpdateOneModel<>(filter, combinedThingWrites.getSequenceNumberUpdate()));
        final List<WriteModel<Document>> policyWriteModels = createPolicyWriteModels(combinedThingWrites
                .getCombinedPolicyUpdates());
        final BulkWriteOptions option = new BulkWriteOptions();
        option.ordered(true);

        return Source.fromPublisher(collection.bulkWrite(writeModels, option))
                .flatMapConcat(r -> {
                    if (r.getModifiedCount() > 0 || r.getInsertedCount() > 0) {
                        if (!policyWriteModels.isEmpty()) {
                            return Source.fromPublisher(policiesCollection.bulkWrite(policyWriteModels, option))
                                    .map(r2 -> Boolean.TRUE);
                        } else {
                            return Source.single(Boolean.TRUE);
                        }
                    } else {
                        return Source.single(Boolean.FALSE);
                    }
                })
                .recoverWithRetries(1, createErrorRecovery(thingId));
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

    @Override
    public Source<Boolean, NotUsed> updatePolicy(final Thing thing, final PolicyEnforcer policyEnforcer) {
        if (thing == null || policyEnforcer == null) {
            log.warning("Thing or policyEnforcer was null when trying to update policy search index. Thing: <{}>, " +
                    "PolicyEnforcer: <{}>", thing, policyEnforcer);
            return Source.single(Boolean.FALSE);
        }

        final PolicyUpdate policyUpdate = PolicyUpdateFactory.createPolicyIndexUpdate(thing, policyEnforcer);
        final Bson filter = filterWithEqualThingId(getThingId(thing));
        final List<UpdateOneModel<Document>> writeThingIndexModels = createThingIndexModels(filter, policyUpdate);
        final BulkWriteOptions option = new BulkWriteOptions();
        option.ordered(true);

        return Source.fromPublisher(collection.bulkWrite(writeThingIndexModels, option))
                .flatMapConcat(result -> {
                            if (result.getMatchedCount() > 0) {
                                final List<WriteModel<Document>> writePolicyIndexModels =
                                        createPolicyIndexModels(policyUpdate.getPolicyIndexRemoveFilter(),
                                                policyUpdate.getPolicyIndexInsertEntries());
                                return Source.fromPublisher(
                                        policiesCollection.bulkWrite(writePolicyIndexModels, option))
                                        .map(result2 -> Boolean.TRUE);
                            } else {
                                return Source.single(Boolean.FALSE);
                            }
                        }
                )
                .recoverWithRetries(1, createErrorRecovery(thing.getId().orElse(null)));
    }

    @Override
    public Source<List<String>, NotUsed> getThingIdsForPolicy(final String policyId) {
        final Bson filter = eq(FIELD_POLICY_ID, policyId);
        return Source.fromPublisher(collection.find(filter)
                .projection(new BsonDocument(FIELD_ID, new BsonInt32(1))))
                .map(doc -> doc.getString(FIELD_ID))
                .fold(new ArrayList<String>(), (list, id) -> {
                    list.add(id);
                    return list;
                });
    }

    @Override
    public Source<ThingMetadata, NotUsed> getThingMetadata(final String thingId) {
        final Bson filter = eq(FIELD_ID, thingId);

        return Source.fromPublisher(collection.find(filter)
                .projection(Projections.include(FIELD_REVISION, FIELD_POLICY_ID, FIELD_POLICY_REVISION)))
                .map(doc -> {
                    if (null == doc) {
                        return new ThingMetadata(-1L, null, -1L);
                    } else {
                        return new ThingMetadata(
                                doc.containsKey(FIELD_REVISION) ? doc.getLong(FIELD_REVISION) : -1L,
                                doc.containsKey(FIELD_POLICY_ID) ? doc.getString(FIELD_POLICY_ID) : null,
                                doc.containsKey(FIELD_POLICY_REVISION) ? doc.getLong(FIELD_POLICY_REVISION) : -1L);
                    }
                });
    }

    private static String getThingId(final Thing thing) {
        return thing.getId().orElseThrow(() -> new IllegalArgumentException("The thing has no ID!"));
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

    private PartialFunction<Throwable, Source<Boolean, NotUsed>> createErrorRecovery(final String id) {
        return new PFBuilder<Throwable, Source<Boolean, NotUsed>>()
                .matchAny(throwable -> {
                    final String msgTemplate = "Update operation for <{}> failed due to";
                    if (isErrorOfType(MONGO_DUPLICATE_KEY_ERROR_CODE, throwable)) {
                        // expected error - thus as DEBUG:
                        log.debug(msgTemplate + " a duplicate key: {}", id, throwable.getMessage());
                        return Source.single(Boolean.FALSE);
                    } else if (isErrorOfType(MONGO_INDEX_VALUE_ERROR_CODE, throwable)) {
                        log.error(throwable, msgTemplate + " a too large value which cannot be indexed!", id);
                        return Source.single(Boolean.TRUE);
                    } else {
                        log.error(throwable, msgTemplate + " an unexpected error: {}", id, throwable.getMessage());
                        return Source.failed(throwable);
                    }
                })
                .build();
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
