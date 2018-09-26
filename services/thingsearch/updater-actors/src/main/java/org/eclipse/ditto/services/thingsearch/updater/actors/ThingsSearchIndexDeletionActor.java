/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_COLLECTION_NAME;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.annotation.Nullable;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Cluster singleton actor taking care of deleting marked as deleted Things (containing a "__deleted" field in the
 * "thingEntities" collection) from the search index after a configurable {@code age}.
 */
public final class ThingsSearchIndexDeletionActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    static final String ACTOR_NAME = "thingsSearchIndexDeletionActor";

    private static final Object PERFORM_DELETION_MESSAGE = new Object();

    private final DiagnosticLoggingAdapter log = Logging.apply(this);

    private final Duration age;
    private final Duration runInterval;
    private final int firstIntervalHour;
    private final MongoCollection<Document> collection;
    private final Materializer actorMaterializer;

    @Nullable private Cancellable scheduler = null;

    private ThingsSearchIndexDeletionActor(final MongoClientWrapper mongoClientWrapper) {
        final Config config = getContext().getSystem().settings().config();
        age = config.getDuration(ConfigKeys.DELETION_AGE);
        runInterval = config.getDuration(ConfigKeys.DELETION_RUN_INTERVAL);
        firstIntervalHour = config.getInt(ConfigKeys.DELETION_FIRST_INTERVAL_HOUR);
        if (firstIntervalHour < 0 || firstIntervalHour > 23) {
            throw new ConfigurationException(
                    "The configured <" + ConfigKeys.DELETION_FIRST_INTERVAL_HOUR + "> must be" +
                            "between 0 and 23");
        }

        actorMaterializer = ActorMaterializer.create(getContext());
        collection = mongoClientWrapper.getDatabase().getCollection(THINGS_COLLECTION_NAME);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param mongoClientWrapper the MongoDB client wrapper to use for deletion.
     * @return the Akka configuration Props object.
     */
    public static Props props(final MongoClientWrapper mongoClientWrapper) {
        return Props.create(ThingsSearchIndexDeletionActor.class, mongoClientWrapper);
    }

    @Override
    public void preStart() {

        final Instant now = Instant.now();
        final Duration initialDelay = calculateInitialDelay(now, firstIntervalHour);
        log.info("Initial deletion is scheduled at <{}>", now.plus(initialDelay));

        scheduler = getContext().getSystem().scheduler()
                .schedule(initialDelay, runInterval, getSelf(), PERFORM_DELETION_MESSAGE, getContext().dispatcher(),
                        getSelf());
    }

    @Override
    public void postStop() {
        if (scheduler != null && !scheduler.isCancelled()) {
            scheduler.cancel();
            scheduler = null;
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(PERFORM_DELETION_MESSAGE, obj -> performDeletion())
                .matchAny(a -> log.warning("Got unknown message: <{}>", a))
                .build();
    }

    private void performDeletion() {

        final Date deletionDate = Date.from(Instant.now().minus(age).truncatedTo(ChronoUnit.SECONDS));
        final Bson deleteFilter = Filters.lte(PersistenceConstants.FIELD_DELETED, deletionDate);
        log.info("About to delete marked as deleted fields in collection <{}> matching the filter: <{}>",
                THINGS_COLLECTION_NAME, deleteFilter);

        Source.fromPublisher(collection.deleteMany(deleteFilter))
                .runWith(Sink.head(), actorMaterializer)
                .whenComplete((deleteResult, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable, "Deletion of marked as deleted Things failed due to: {}: <{}>",
                                throwable.getClass().getSimpleName(), throwable.getMessage());
                    } else {
                        log.info("Deletion of marked as deleted Things was successful, response: {}",
                                deleteResult);
                    }
                });
    }

    /**
     * Calculates on the passed {@code now} Instant and the passed {@code firstIntervalHourUtc} the initial Duration.
     * The Duration will be a max. of 23 hours and 59 minutes and a min. of 0 hours, 0 minutes if {@code now} matches
     * exactly the current hour of the day.
     *
     * @param now the "now" Instant, passed in in order to test this method.
     * @param firstIntervalHourUtc the first hour to schedule at in UTC time.
     * @return the Duration of how long to wait until the first hour arrices
     */
    static Duration calculateInitialDelay(final Instant now, final int firstIntervalHourUtc) {
        final LocalDateTime localDateTime = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        final int currentHour = localDateTime.getHour();
        final int currentMinute = localDateTime.getMinute();

        final Duration durationSinceMidnight = Duration.ofHours(currentHour).plusMinutes(currentMinute);
        final Duration delay = Duration.ofHours(firstIntervalHourUtc)
                .minus(durationSinceMidnight);
        if (delay.isNegative()) {
            return Duration.ofHours(24).plus(delay);
        } else {
            return delay;
        }
    }

}
