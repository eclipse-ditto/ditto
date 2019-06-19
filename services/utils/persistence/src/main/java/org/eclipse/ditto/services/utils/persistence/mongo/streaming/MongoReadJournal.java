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
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.QueryOperators;
import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.ListCollectionsPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.JavaDslMongoReadJournal;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;
import akka.persistence.query.PersistenceQuery;
import akka.stream.javadsl.Source;

/**
 * Reads the event journal of com.github.scullxbones.akka-persistence-mongo plugin.
 * In the Akka system configuration,
 * <ul>
 * <li>
 * {@code akka.persistence.journal.auto-start-journals} must contain exactly 1 configuration key {@code
 * <JOURNAL_KEY>},
 * </li>
 * <li>
 * {@code <JOURNAL_KEY>.overrides.journal-collection} must be defined and equal to the name of the event journal
 * collection.
 * </li>
 * </ul>
 */
@AllValuesAreNonnullByDefault
public class MongoReadJournal {
    // not a final class to test with Mockito

    private static final String AKKA_PERSISTENCE_JOURNAL_AUTO_START_JOURNALS =
            "akka.persistence.journal.auto-start-journals";

    private static final String JOURNAL_COLLECTION_NAME_KEY = "overrides.journal-collection";

    private static final String METADATA_COLLECTION_NAME_KEY = "overrides.metadata-collection";

    private static final String ID = JournallingFieldNames$.MODULE$.ID();
    private static final String PROCESSOR_ID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();
    private static final String MAX_SN = JournallingFieldNames$.MODULE$.MAX_SN();
    private static final String TO = JournallingFieldNames$.MODULE$.TO();
    private static final String GTE = QueryOperators.GTE;
    private static final String LT = QueryOperators.LT;

    private static final Integer PROJECT_INCLUDE = 1;
    private static final Integer SORT_DESCENDING = -1;
    private static final Integer SORT_ASCENDING = 1;

    private static final Document JOURNAL_PROJECT_DOCUMENT =
            toDocument(new Object[][]{{PROCESSOR_ID, PROJECT_INCLUDE}, {TO, PROJECT_INCLUDE}});

    private static final Document JOURNAL_SORT_DOCUMENT = toDocument(new Object[][]{{ID, SORT_DESCENDING}});

    private static final Document METADATA_SORT_DOCUMENT = toDocument(new Object[][]{{PROCESSOR_ID, SORT_ASCENDING}});

    private static final String COLLECTION_NAME_FIELD = "name";

    /**
     * Concurrently consumes this amount of streams from different journals (if used with namespace suffixed
     * collections).
     */
    private static final int CONCURRENT_JOURNAL_READS = 5;

    private final Pattern journalCollectionPrefix;
    private final MongoCollection<Document> metadataCollection;
    private final DittoMongoClient mongoClient;
    private final String autoStartJournalConfigKey;
    private final Logger log;

    private MongoReadJournal(final Pattern journalCollectionPrefix, final String metadataCollectionName,
            final DittoMongoClient mongoClient, final String autoStartJournalConfigKey) {
        this.journalCollectionPrefix = journalCollectionPrefix;
        this.metadataCollection = mongoClient.getCollection(metadataCollectionName);
        this.mongoClient = mongoClient;
        this.autoStartJournalConfigKey = autoStartJournalConfigKey;
        log = LoggerFactory.getLogger(MongoTimestampPersistence.class);
    }

    /**
     * Creates a new {@code MongoReadJournal}.
     *
     * @param config The Akka system configuration.
     * @param mongoClient The Mongo client wrapper.
     * @return A {@code MongoReadJournal} object.
     */
    public static MongoReadJournal newInstance(final Config config, final DittoMongoClient mongoClient) {
        final String autoStartJournalConfigKey = extractAutoStartJournalConfigKey(config);
        final Config journalConfig = config.getConfig(autoStartJournalConfigKey);
        final Pattern journalCollectionPrefix = resolveJournalCollectionPrefix(journalConfig);
        final String metadataCollectionName = journalConfig.getString(METADATA_COLLECTION_NAME_KEY);
        return new MongoReadJournal(journalCollectionPrefix, metadataCollectionName, mongoClient,
                autoStartJournalConfigKey);
    }

    /**
     * Create a {@code JavaDslMongoReadJournal} for the event journal known to this object.
     *
     * @return source of the content of the entire metadata collection.
     */
    public JavaDslMongoReadJournal toJavaDslMongoReadJournal(final ActorSystem actorSystem) {
        return PersistenceQuery.get(actorSystem)
                .getReadJournalFor(JavaDslMongoReadJournal.class, autoStartJournalConfigKey);
    }

    /**
     * Retrieve sequence numbers for persistence IDs modified within the time interval as a source of {@code
     * PidWithSeqNr}. A persistence ID may appear multiple times with various sequence numbers.
     *
     * @param start start of the time window.
     * @param end end of the time window.
     * @return source of persistence IDs and sequence numbers written within the given time window.
     */
    public Source<PidWithSeqNr, NotUsed> getPidWithSeqNrsByInterval(final Instant start, final Instant end) {
        final MongoDatabase database = mongoClient.getDefaultDatabase();
        final Document filterDocument = createFilterObject(start, end);

        log.debug("Looking for journal collection with pattern <{}>.", journalCollectionPrefix);

        return resolveJournalCollectionNames(journalCollectionPrefix, database, log)
                .map(database::getCollection)
                .map(journal -> journal.find(filterDocument, Document.class)
                        .projection(JOURNAL_PROJECT_DOCUMENT)
                        .sort(JOURNAL_SORT_DOCUMENT)
                )
                .map(Source::fromPublisher)
                .flatMapMerge(CONCURRENT_JOURNAL_READS, source -> source
                        .map(doc -> new PidWithSeqNr(doc.getString(PROCESSOR_ID), doc.getLong(TO)))
                );
    }

    private Document createFilterObject(final Instant start, final Instant end) {
        final ObjectId startObjectId = instantToObjectIdBoundary(start);
        final ObjectId endObjectId = instantToObjectIdBoundary(end.plus(1L, ChronoUnit.SECONDS));
        log.debug("Limiting query to ObjectIds $gte {} and $lt {}", startObjectId, endObjectId);
        return toDocument(new Object[][]{
                {ID, toDocument(new Object[][]{
                        {GTE, startObjectId},
                        {LT, endObjectId}
                })}
        });
    }

    /* Create a ObjectID boundary from a timestamp to be used for comparison in MongoDB queries. */
    private static ObjectId instantToObjectIdBoundary(final Instant instant) {
        // MongoDBObject IDs only contain dates with precision of seconds, thus adjust the range of the query
        // appropriately to make sure a client does not miss data when providing Instants with higher precision.
        //
        // Do not use
        //
        //   new ObjectId(Date.from(startTruncatedToSecs))
        //
        // to compute object ID boundaries. The 1-argument constructor above appends incidental non-zero bits after
        // the timestamp and may filter out events persisted after 'instant' if they happen to have
        // a lower machine ID, process ID or counter value. (A MongoDB ObjectID is a byte array with fields for
        // timestamp, machine ID, process ID and counter such that timestamp occupies the most significant bits.)
        return new ObjectId(Date.from(instant.truncatedTo(ChronoUnit.SECONDS)), 0, (short) 0, 0);
    }

    private static Document toDocument(final Object[][] keyValuePairs) {
        final Map<String, Object> map = new HashMap<>(keyValuePairs.length);
        for (final Object[] keyValuePair : keyValuePairs) {
            map.put(keyValuePair[0].toString(), keyValuePair[1]);
        }
        return new Document(map);
    }

    /**
     * Extract the auto-start journal config from the configuration of the actor system.
     * <p>
     * It assumes that in the Akka system configuration,
     * {@code akka.persistence.journal.auto-start-journals} contains exactly 1 configuration key {@code
     * <JOURNAL_KEY>}, which points to the configuration of the auto-start journal.
     *
     * @param config The key to the configuration of the auto-start journal.
     */
    private static String extractAutoStartJournalConfigKey(final Config config) {
        final List<String> autoStartJournals = config.getStringList(AKKA_PERSISTENCE_JOURNAL_AUTO_START_JOURNALS);
        if (autoStartJournals.size() != 1) {
            final String message = String.format("Expect %s to be a singleton list, but it is List(%s)",
                    AKKA_PERSISTENCE_JOURNAL_AUTO_START_JOURNALS,
                    String.join(", ", autoStartJournals));
            throw new IllegalArgumentException(message);
        } else {
            return autoStartJournals.get(0);
        }
    }

    /**
     * Resolve event journal collection prefix (e.g. "things_journal") from the auto-start journal configuration.
     * <p>
     * It assumes that in the auto-start journal configuration,
     * {@code overrides.journal-collection} is defined and equal to the name of the event journal
     * collection.
     *
     * @param journalConfig The journal configuration.
     * @return The name of the event journal collection.
     * @throws IllegalArgumentException if {@code akka.persistence.journal.auto-start-journal} is not a singleton list.
     * @throws com.typesafe.config.ConfigException.Missing if a relevant config value is missing.
     * @throws com.typesafe.config.ConfigException.WrongType if a relevant config value has not the expected type.
     */
    private static Pattern resolveJournalCollectionPrefix(final Config journalConfig) {
        final String journalCollectionPrefix = journalConfig.getString(JOURNAL_COLLECTION_NAME_KEY);
        return Pattern.compile("^" + journalCollectionPrefix + ".*");
    }

    /**
     * Resolves all event journal collection names starting with the passed {@code journalCollectionPrefix}.
     *
     * @param journalCollectionPrefix the prefix of the journal collections to resolve.
     * @param database the MongoDB database to use for resolving collection names.
     * @return a source of resolved journal collection names which matched the prefix.
     */
    private static Source<String, NotUsed> resolveJournalCollectionNames(final Pattern journalCollectionPrefix,
            final MongoDatabase database, final Logger log) {

        // starts with "journalCollectionPrefix":
        final ListCollectionsPublisher<Document> documentListCollectionsPublisher = database.listCollections();
        final Publisher<Document> publisher =
                documentListCollectionsPublisher.filter(Filters.regex(COLLECTION_NAME_FIELD, journalCollectionPrefix));
        return Source.fromPublisher(publisher)
                .map(document -> document.getString(COLLECTION_NAME_FIELD))
                // Double check in case the Mongo API persistence layer in use does not support listCollections with filtering
                .filter(collectionName -> journalCollectionPrefix.matcher(collectionName).matches())
                .map(collectionName -> {
                    log.debug("Journal collection <{}> with pattern <{}> found.", collectionName,
                            journalCollectionPrefix);
                    return collectionName;
                });
    }

}
