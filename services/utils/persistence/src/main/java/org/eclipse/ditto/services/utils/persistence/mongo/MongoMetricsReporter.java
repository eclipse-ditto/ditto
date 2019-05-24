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
package org.eclipse.ditto.services.utils.persistence.mongo;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.metrics.mongo.MongoMetricsBuilder;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Reports on the timers of {@link org.eclipse.ditto.services.utils.metrics.mongo.MongoMetricsBuilder}.
 */
public final class MongoMetricsReporter extends AbstractHealthCheckingActor {

    private static final String ACTOR_NAME = MongoMetricsReporter.class.getSimpleName();

    private static final Tick TICK = new Tick();

    private final Duration resolution;
    private final int history;
    private final Deque<Long> maxTimerNanos;

    private MongoMetricsReporter(final Duration resolution, final int history, final ActorRef pubSubMediator) {
        this.resolution = resolution;
        this.history = Math.max(1, history);
        maxTimerNanos = new ArrayDeque<>(history);

        getTimers().startPeriodicTimer(TICK, TICK, resolution);

        subscribeForTopicWithoutGroup(pubSubMediator);
    }

    /**
     * Create Props for Mongo metrics reporter.
     *
     * @param resolution How far apart each measurement is.
     * @param history How many historical items to keep.
     * @return Props for creating healthMongo metrics reporters.
     */
    public static Props props(final Duration resolution, final int history, final ActorRef pubSubMediator) {
        return Props.create(MongoMetricsReporter.class,
                () -> new MongoMetricsReporter(resolution, history, pubSubMediator));
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .matchEquals(TICK, this::tick)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        updateHealth(renderStatusInfo());
    }

    private StatusInfo renderStatusInfo() {
        return StatusInfo.fromStatus(StatusInfo.Status.UP, Collections.singletonList(renderStatusDetailMessage()));
    }

    private StatusDetailMessage renderStatusDetailMessage() {
        return StatusDetailMessage.of(StatusDetailMessage.Level.INFO, render());
    }

    private JsonObject render() {
        final JsonArray maxTimeNanosJsonArray =
                maxTimerNanos.stream().map(JsonFactory::newValue).collect(JsonCollectors.valuesToArray());
        return JsonFactory.newObjectBuilder()
                .set("resolution", resolution.toString())
                .set("maxTimerNanos", maxTimeNanosJsonArray)
                .build();
    }

    private void tick(final Tick trigger) {
        final long nanos = MongoMetricsBuilder.maxTimerNanos().getThenReset();
        if (maxTimerNanos.size() >= history) {
            maxTimerNanos.removeLast();
        }
        maxTimerNanos.addFirst(nanos);
    }

    private void subscribeForTopicWithoutGroup(final ActorRef pubSubMediator) {
        final Object subscribe = new DistributedPubSubMediator.Subscribe(ACTOR_NAME, getSelf());
        pubSubMediator.tell(subscribe, getSelf());
    }

    private void subscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.info("Subscribed: {}", subscribeAck.subscribe());
    }

    private static final class Tick {}
}
