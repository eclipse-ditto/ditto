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
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoCollection;

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

    private final MongoCollection<Document> collection;
    private final Duration age;
    private final Duration runInterval;
    private final int firstIntervalHour;
    private final Materializer actorMaterializer;
    private final DiagnosticLoggingAdapter log;

    @Nullable private Cancellable scheduler;

    private ThingsSearchIndexDeletionActor(final MongoCollection<Document> collection,
            final Duration age,
            final Duration runInterval,
            final int firstIntervalHour) {

        this.collection = collection;
        this.age = age;
        this.runInterval = runInterval;
        this.firstIntervalHour = firstIntervalHour;
        actorMaterializer = ActorMaterializer.create(getContext());
        log = Logging.apply(this);
        scheduler = null;
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param collection the collection to perform the deletions on.
     * @param age the amount of time after which thing entities should be deleted.
     * @param runInterval the interval of physical deletion.
     * @param firstIntervalHour the hour (UTC) of the first physical deletion run.
     * @return the Akka configuration Props object.
     */
    public static Props props(final MongoCollection<Document> collection,
            final Duration age,
            final Duration runInterval,
            final int firstIntervalHour) {

        return Props.create(ThingsSearchIndexDeletionActor.class, collection, age, runInterval, firstIntervalHour);
    }

    @Override
    public void preStart() {
        final Instant now = Instant.now();
        final Duration initialDelay = calculateInitialDelay(now, firstIntervalHour);
        log.info("Initial deletion is scheduled at <{}>.", now.plus(initialDelay));

        final ActorContext actorContext = getContext();
        scheduler = actorContext.getSystem()
                .scheduler()
                .schedule(initialDelay, runInterval, getSelf(), PERFORM_DELETION_MESSAGE, actorContext.dispatcher(),
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
        final Bson deleteFilter = Filters.and(
                Filters.eq(PersistenceConstants.FIELD_DELETED_FLAG, true),
                Filters.lte(PersistenceConstants.FIELD_DELETED, deletionDate));
        log.info("Going to delete marked as deleted fields in collection <{}> matching the filter: <{}>.",
                THINGS_COLLECTION_NAME, deleteFilter);

        Source.fromPublisher(collection.deleteMany(deleteFilter))
                .runWith(Sink.head(), actorMaterializer)
                .whenComplete((deleteResult, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable, "Deletion of marked as deleted things failed due to: {}: <{}>",
                                throwable.getClass().getSimpleName(), throwable.getMessage());
                    } else {
                        log.info("Deletion of marked as deleted things was successful, response: {}", deleteResult);
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
        }
        return delay;
    }

}
