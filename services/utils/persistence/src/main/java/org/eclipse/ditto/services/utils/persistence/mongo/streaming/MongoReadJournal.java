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
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.QueryOperators;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.contrib.persistence.mongodb.JournallingFieldNames$;
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

    private static final String JOURNAL_COLLECTION_NAME_SUFFIX = ".overrides.journal-collection";

    private static final String ID = JournallingFieldNames$.MODULE$.ID();
    private static final String PROCESSOR_ID = JournallingFieldNames$.MODULE$.PROCESSOR_ID();
    private static final String TO = JournallingFieldNames$.MODULE$.TO();
    private static final String GTE = QueryOperators.GTE;
    private static final String LT = QueryOperators.LT;

    private static final Integer PROJECT_INCLUDE = 1;
    private static final Integer SORT_DESCENDING = -1;

    private static final Document PROJECT_DOCUMENT =
            toDocument(new Object[][]{{PROCESSOR_ID, PROJECT_INCLUDE}, {TO, PROJECT_INCLUDE}});

    private static final Document SORT_DOCUMENT = toDocument(new Object[][]{{ID, SORT_DESCENDING}});

    private final Logger log;

    private final MongoCollection<Document> journal;

    private MongoReadJournal(final MongoCollection<Document> journal) {
        this.journal = journal;
        this.log = LoggerFactory.getLogger(MongoSearchSyncPersistence.class);
    }

    /**
     * Creates a new {@code MongoReadJournal}.
     *
     * @param config The Akka system configuration.
     * @param clientWrapper The Mongo client wrapper.
     * @return A {@code MongoReadJournal} object.
     */
    public static MongoReadJournal newInstance(final Config config, final MongoClientWrapper clientWrapper) {
        final String journalCollectionName = resolveJournalCollectionName(config);
        final MongoCollection<Document> journal = clientWrapper.getDatabase().getCollection(journalCollectionName);
        return new MongoReadJournal(journal);
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

        final Document filterDocument = createFilterObject(start, end);

        final Publisher<Document> publisher =
                journal.find(filterDocument, Document.class).projection(PROJECT_DOCUMENT).sort(SORT_DOCUMENT);

        return Source.fromPublisher(publisher)
                .map(doc -> new PidWithSeqNr(doc.getString(PROCESSOR_ID), doc.getLong(TO)));
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
        for (Object[] keyValuePair : keyValuePairs) {
            map.put(keyValuePair[0].toString(), keyValuePair[1]);
        }
        return new Document(map);
    }

    /**
     * Resolve event journal collection name from an Akka configuration object.
     * <p>
     * It assumes that in the Akka system configuration,
     * <ul>
     * <li>
     * {@code akka.persistence.journal.auto-start-journals} contains exactly 1 configuration key {@code
     * <JOURNAL_KEY>},
     * </li>
     * <li>
     * {@code <JOURNAL_KEY>.overrides.journal-collection} is defined and equal to the name of the event journal
     * collection.
     * </li>
     * </ul>
     * </p>
     *
     * @param config The configuration.
     * @return The name of the event journal collection.
     * @throws IllegalArgumentException if {@code akka.persistence.journal.auto-start-journal} is not a singleton list.
     * @throws com.typesafe.config.ConfigException.Missing if a relevant config value is missing.
     * @throws com.typesafe.config.ConfigException.WrongType if a relevant config value has not the expected type.
     */
    private static String resolveJournalCollectionName(final Config config) {
        final List<String> autoStartJournals = config.getStringList(AKKA_PERSISTENCE_JOURNAL_AUTO_START_JOURNALS);
        if (autoStartJournals.size() != 1) {
            final String message = String.format("Expect %s to be a singleton list, but it is List(%s)",
                    AKKA_PERSISTENCE_JOURNAL_AUTO_START_JOURNALS,
                    autoStartJournals.stream().collect(Collectors.joining(", ")));
            throw new IllegalArgumentException(message);
        } else {
            final String journalKey = autoStartJournals.get(0);
            final String journalCollectionNameKey = journalKey + JOURNAL_COLLECTION_NAME_SUFFIX;
            return config.getString(journalCollectionNameKey);
        }
    }
}
