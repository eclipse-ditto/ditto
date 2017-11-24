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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamForwarder;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * This actor is responsible for one cycle of synchronization of all things changed since the last successful full
 * synchronization.
 */
public class ThingsStreamForwarder extends AbstractStreamForwarder<ThingTag> {

    private final ActorRef thingsUpdater;
    private final Duration maxIdleTime;

    private ThingsStreamForwarder(final ActorRef thingsUpdater, final Duration maxIdleTime) {
        this.thingsUpdater = thingsUpdater;
        this.maxIdleTime = maxIdleTime;
    }

    /**
     * Creates a {@code Props} object to instantiate this actor.
     *
     * @param thingsUpdater Reference of a {@code ThingsUpdater}, the recipient of streamed {@code ThingTag} messages.
     * @param maxIdleTime Maximum time this actor stays alive without receiving any message.
     * @return The {@code Props} object.
     */
    public static Props props(final ActorRef thingsUpdater, final Duration maxIdleTime) {
        return Props.create(ThingsStreamForwarder.class, () -> new ThingsStreamForwarder(thingsUpdater, maxIdleTime));
    }

    // TODO: test
    @Override
    protected void onSuccess() {
        super.onSuccess();
        // TODO: make amount to substract configurable
        final LocalDateTime timestamp = LocalDateTime.now(Clock.systemUTC())
                .minus(5, ChronoUnit.MINUTES);
        final SearchSynchronizationSuccess successMessage = SearchSynchronizationSuccess.newInstance(timestamp);
        // TODO: make timeout configurable
        final Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
        // TODO: fire-and-forget or sync?
        final CompletableFuture ft = PatternsCS.ask(thingsUpdater, successMessage, timeout).toCompletableFuture();
        // wait for the result result
        final Object result = ft.join();
        if (Boolean.TRUE.equals(result)) {
            log.info("Updating the last successful search sync timestamp was successful.");
        } else {
            log.warning("Updating the last successful search sync timestamp was unsuccessful.");
        }
    }

    @Override
    protected ActorRef getRecipient() {
        return thingsUpdater;
    }

    @Override
    protected Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    protected Class<ThingTag> getElementClass() {
        return ThingTag.class;
    }
}
