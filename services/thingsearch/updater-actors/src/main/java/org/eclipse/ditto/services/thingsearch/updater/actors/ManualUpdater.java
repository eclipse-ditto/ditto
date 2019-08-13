/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.Duration;
import java.util.Optional;

import org.bson.Document;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.DelayOverflowStrategy;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Cluster singleton to trigger index updates from a collection.
 */
final class ManualUpdater extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "manualUpdater";

    /**
     * Name of the collection to trigger updates.
     */
    static final String COLLECTION_NAME = "searchThingsManualUpdates";

    /**
     * Field containing ID of the thing in the collection to trigger updates.
     */
    static final String ID_FIELD = "id";

    /**
     * Field containing revision of the thing in the collection to trigger updates.
     */
    static final String REVISION = "revision";

    /**
     * Interval at which to process the elements in the collection.
     */
    private static final Duration DELAY_PER_ELEMENT = Duration.ofSeconds(1L);

    /**
     * Interval at which to check for new elements in the collection.
     */
    private static final Duration DELAY_PER_CURSOR = Duration.ofMinutes(1L);

    private static final Duration MIN_BACK_OFF = Duration.ofSeconds(1L);

    private static final Duration MAX_BACK_OFF = Duration.ofSeconds(1L);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @SuppressWarnings("unused")
    private ManualUpdater(final MongoDatabase database, final ActorRef thingsUpdater) {
        this(database, thingsUpdater, DELAY_PER_ELEMENT, DELAY_PER_CURSOR, MIN_BACK_OFF, MAX_BACK_OFF);
    }

    @SuppressWarnings("unused")
    ManualUpdater(final MongoDatabase database,
            final ActorRef thingsUpdater,
            final Duration delayPerElement,
            final Duration delayPerCursor,
            final Duration minBackOff,
            final Duration maxBackOff) {

        final Source<ThingTag, NotUsed> restartSource =
                RestartSource.onFailuresWithBackoff(minBackOff, maxBackOff, 1.0,
                        () -> retrieveAllThingTagsInCollection(database, delayPerElement, delayPerCursor));

        final Sink<ThingTag, ?> sink =
                Sink.foreach(thingTag -> thingsUpdater.tell(thingTag, ActorRef.noSender()));

        // run stream in this actor's context so that they stop on this actor's termination
        restartSource.to(sink).run(ActorMaterializer.create(getContext()));
    }

    /**
     * Create Props object for this actor.
     *
     * @param db Mongo database in which to find IDs of things to update.
     * @param thingsUpdater target of messages from this actor.
     * @return Props for this actor.
     */
    public static Props props(final MongoDatabase db, final ActorRef thingsUpdater) {

        return Props.create(ManualUpdater.class, db, thingsUpdater);
    }

    @Override
    public Receive createReceive() {
        return emptyBehavior();
    }

    private Source<ThingTag, NotUsed> retrieveAllThingTagsInCollection(final MongoDatabase db,
            final Duration delayPerElement, final Duration delayPerCursor) {

        return Source.repeat(NotUsed.getInstance())
                .buffer(1, OverflowStrategy.backpressure())
                .delay(delayPerElement, DelayOverflowStrategy.backpressure())
                .flatMapConcat(notUsed -> retrieveThingTagOrElseDelay(db, delayPerCursor));
    }

    private Source<ThingTag, NotUsed> retrieveThingTagOrElseDelay(final MongoDatabase db,
            final Duration delayPerCursor) {

        return Source.fromPublisher(db.getCollection(COLLECTION_NAME).findOneAndDelete(new Document()))
                .buffer(1, OverflowStrategy.backpressure())
                .map(this::convertToThingTag)
                .orElse(Source.single(Optional.<ThingTag>empty()).initialDelay(delayPerCursor))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .log("ManualUpdater", log);
    }

    private Optional<ThingTag> convertToThingTag(final Document document) {
        try {
            final String id = document.getString(ID_FIELD);
            final ThingId thingId = ThingId.of(id);
            final long revision = document.getLong(REVISION);
            return Optional.of(ThingTag.of(thingId, revision));
        } catch (final ClassCastException | NullPointerException e) {
            log.debug("Failed to convert doc '{}' to ThingTag: [{}] {}",
                    document != null ? document.toString() : "<null>",
                    e.getClass().getName(), e.getMessage());
            return Optional.empty();
        }
    }

}
