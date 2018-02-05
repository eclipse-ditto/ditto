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
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.SET;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.UNSET;

import java.util.ArrayList;
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
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.mapping.ThingDocumentMapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.AbstractThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.reactivestreams.Publisher;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import com.mongodb.bulk.BulkWriteResult;
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
    private final EventToPersistenceStrategyFactory<Bson, PolicyUpdate>
            persistenceStrategyFactory;

    /**
     * Constructor.
     *
     * @param clientWrapper the client wrapper holding the connection information.
     * @param log the logger to use for logging.
     * @param persistenceStrategyFactory The persistence strategy factory to use.
     */
    public MongoThingsSearchUpdaterPersistence(final MongoClientWrapper clientWrapper,
            final LoggingAdapter log,
            final EventToPersistenceStrategyFactory<Bson, PolicyUpdate> persistenceStrategyFactory) {
        super(log);
        collection = clientWrapper.getDatabase().getCollection(THINGS_COLLECTION_NAME);
        policiesCollection = clientWrapper.getDatabase().getCollection(POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME);

        this.persistenceStrategyFactory = persistenceStrategyFactory;
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

    private static Bson filterWithLowerThingRevisionOrLowerPolicyRevision(final String thingId,
            final long revision, final long policyRevision) {
        // In case of a policy update, it is ok when the current thing revision is equal to new one (must not be
        // less than!
        final Bson thingLowerRevision = lt(FIELD_REVISION, revision);
        final Bson policyLowerRevision = and(
                lt(FIELD_POLICY_REVISION, policyRevision),
                lte(FIELD_REVISION, revision));
        return and(eq(FIELD_ID, thingId), or(thingLowerRevision, policyLowerRevision));
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
        log.debug("Saving Thing with revision <{}> and policy revision <{}>: <{}>", revision, policyRevision, thing);
        final Bson filter = filterWithLowerThingRevisionOrLowerPolicyRevision(getThingId(thing), revision, policyRevision);
        final Document document = toUpdate(ThingDocumentMapper.toDocument(thing), revision, policyRevision);
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
        log.debug("Deleting Thing with ThingId <{}>", thingId);
        final Bson filter = eq(FIELD_ID, thingId);
        final Bson document = new Document(SET, new Document(FIELD_DELETED, new Date()));
        return delete(thingId, filter, document);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<Boolean, NotUsed> delete(final String thingId, final long revision) {
        log.debug("Deleting Thing with ThingId <{}> and revision <{}>", thingId, revision);
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
     * Executes the passed in events using a bulk write operation on MongoDB. The bulk write is performed with the
     * ordered options which means that if one write fails, the other writes will not be executed.
     *
     * @param thingId the id of the thing to update.
     * @param thingEvents the events to persist in this update.
     * @param policyEnforcer the policy enforcer to enforce the policies.
     * @param targetRevision The target sequence number after all
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    @Override
    public Source<Boolean, NotUsed> executeCombinedWrites(final String thingId,
            final List<ThingEvent> thingEvents,
            final PolicyEnforcer policyEnforcer, final long targetRevision) {
        log.debug("Executing <{}> combined writes for Thing <{}> with target revision <{}>",
                thingEvents.size(), thingId, targetRevision);
        if (!thingEvents.isEmpty()) {
            final BulkWriteOptions writeOrdered = new BulkWriteOptions();
            writeOrdered.ordered(true);

            final long lastKnownRevision = thingEvents.get(0).getRevision() - 1;
            final Bson filter = filterWithExactRevision(thingId, lastKnownRevision);

            // convert the events to the write models
            final List<WriteModel<Document>> thingWriteModels = new ArrayList<>();
            final List<WriteModel<Document>> policyWriteModels = new ArrayList<>();

            for (final ThingEvent thingEvent : thingEvents) {
                final List<WriteModel<Document>> thingUpdates = createThingUpdates(filter, thingEvent);
                final List<WriteModel<Document>> policyUpdates = createPolicyUpdates(thingEvent, policyEnforcer);

                thingWriteModels.addAll(thingUpdates);
                policyWriteModels.addAll(policyUpdates);
            }

            // add the revision update model
            thingWriteModels.add(createRevisionUpdate(filter, targetRevision));

            return Source.fromPublisher(collection.bulkWrite(thingWriteModels, writeOrdered))
                    .flatMapConcat(mapCombinedWritesResult(policyWriteModels))
                    .recoverWithRetries(1, errorRecovery(thingId));

        }
        // return true if nothing to change
        return Source.single(true);
    }

    private <T extends ThingEvent> List<WriteModel<Document>> createThingUpdates(final Bson filter,
            final T thingEvent) {
        final List<Bson> updates = persistenceStrategyFactory
                .getStrategy(thingEvent)
                .thingUpdates(thingEvent, indexLengthRestrictionEnforcer);
        return updates
                .stream()
                .map(update -> new UpdateOneModel<Document>(filter, update, new UpdateOptions().upsert(true)))
                .collect(Collectors.toList());
    }

    private <T extends ThingEvent> List<WriteModel<Document>> createPolicyUpdates(final T thingEvent,
            final PolicyEnforcer policyEnforcer) {
        final List<PolicyUpdate> updates = persistenceStrategyFactory
                .getStrategy(thingEvent)
                .policyUpdates(thingEvent, policyEnforcer);

        final List<WriteModel<Document>> writeModels = new ArrayList<>();
        updates.forEach(policyUpdate -> {
            writeModels.add(new DeleteManyModel<>(policyUpdate.getPolicyIndexRemoveFilter()));
            policyUpdate.getPolicyIndexInsertEntries()
                    .forEach(document -> writeModels.add(new InsertOneModel<>(document)));
        });
        return writeModels;
    }

    private UpdateOneModel<Document> createRevisionUpdate(final Bson thingFilter, final long targetRevision) {
        final Document update = new Document(PersistenceConstants.SET,
                new Document(PersistenceConstants.FIELD_REVISION, targetRevision));
        return new UpdateOneModel<>(thingFilter, update);
    }

    private Function<BulkWriteResult, Source<Boolean, NotUsed>> mapCombinedWritesResult(
            final List<WriteModel<Document>> policyWriteModels) {
        final BulkWriteOptions writeOrdered = new BulkWriteOptions();
        writeOrdered.ordered(true);
        return bulkWriteResult -> {
            if (bulkWriteResult.getModifiedCount() > 0 || bulkWriteResult.getInsertedCount() > 0) {
                if (!policyWriteModels.isEmpty()) {
                    return Source.fromPublisher(policiesCollection.bulkWrite(policyWriteModels, writeOrdered))
                            .map(policiesWriteResult -> Boolean.TRUE);
                } else {
                    return Source.single(Boolean.TRUE);
                }
            } else {
                // return false if the previous bulk write did not modify anything
                return Source.single(Boolean.FALSE);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<Boolean, NotUsed> updatePolicy(final Thing thing, final PolicyEnforcer policyEnforcer) {
        log.debug("Updating policy for Thing: <{}>", thing);
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

    @Override
    public final Source<Set<String>, NotUsed> getThingIdsForPolicy(final String policyId) {
        log.debug("Retrieving Thing ids for policy: <{}>", policyId);
        final Bson filter = eq(FIELD_POLICY_ID, policyId);
        return Source.fromPublisher(collection.find(filter)
                .projection(new BsonDocument(FIELD_ID, new BsonInt32(1))))
                .map(doc -> doc.getString(FIELD_ID))
                .fold(new HashSet<>(), (set, id) -> {
                    set.add(id);
                    return set;
                });
    }

    @Override
    public Source<String, NotUsed> getOutdatedThingIds(final PolicyTag policyTag) {
        log.debug("Retrieving outdated Thing ids with policy tag: <{}>", policyTag);
        final String policyId = policyTag.getId();
        final Bson filter = and(eq(FIELD_POLICY_ID, policyId), lt(FIELD_POLICY_REVISION, policyTag.getRevision()));
        final Publisher<Document> publisher =
                collection.find(filter).projection(new BsonDocument(FIELD_ID, new BsonInt32(1)));
        return Source.fromPublisher(publisher)
                .map(doc -> doc.getString(FIELD_ID));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Source<ThingMetadata, NotUsed> getThingMetadata(final String thingId) {
        log.debug("Retrieving Thing Metadata for Thing: <{}>", thingId);
        final Bson filter = eq(FIELD_ID, thingId);
        return Source.fromPublisher(collection.find(filter)
                .projection(Projections.include(FIELD_REVISION, FIELD_POLICY_ID, FIELD_POLICY_REVISION)))
                .map(mapThingMetadataToModel())
                .orElse(defaultThingMetadata());
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

    private static List<UpdateOneModel<Document>> createThingIndexModels(final Bson filter, final PolicyUpdate update) {
        final List<UpdateOneModel<Document>> updates = new ArrayList<>(3);

        final Bson pullGlobalReads = update.getPullGlobalReads();
        if (pullGlobalReads != null) {
            updates.add(new UpdateOneModel<>(filter, pullGlobalReads));
        }

        final Bson pushGlobalReads = update.getPushGlobalReads();
        if (pushGlobalReads != null) {
            updates.add(new UpdateOneModel<>(filter, pushGlobalReads));
        }

        final Bson pullAclEntries = update.getPullAclEntries();
        if (pullAclEntries != null) {
            updates.add(new UpdateOneModel<>(filter, pullAclEntries));
        }

        return updates;
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
