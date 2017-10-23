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
package org.eclipse.ditto.services.thingsearch.updater.actors.mailbox;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.BoundedDequeBasedMessageQueueSemantics;
import akka.dispatch.DequeBasedMessageQueue;
import akka.dispatch.Envelope;
import akka.dispatch.MailboxType;
import akka.dispatch.MessageQueue;
import akka.dispatch.ProducesMessageQueue;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

/**
 * Mailbox for the package-private actor {@code ThingUpdater}.
 */
public class ThingUpdaterMailbox implements MailboxType,
        ProducesMessageQueue<ThingUpdaterMailbox.ThingUpdaterMessageQueue> {

    private final int capacity;

    /**
     * Creates a new {@code ThingUpdaterMailbox}.
     * This constructor signature must exist, it will be called by Akka.
     *
     * @param settings the ActorSystem settings.
     * @param config the config.
     */
    public ThingUpdaterMailbox(final ActorSystem.Settings settings, final Config config) {
        capacity = config.getInt("mailbox-capacity");
        if (capacity < 1) {
            throw new IllegalArgumentException("Mailbox capacity must not be less than 1");
        }
    }

    @Override
    public MessageQueue create(final scala.Option<ActorRef> owner, final scala.Option<ActorSystem> system) {
        return new ThingUpdaterMessageQueue(capacity, system.get());
    }

    /**
     * Queue for the {@link ThingUpdaterMailbox}. When the {@code capacity} limit is exceeded, the oldest messages will
     * be dropped.
     */
    public static class ThingUpdaterMessageQueue extends LinkedBlockingDeque<Envelope>
            implements DequeBasedMessageQueue,
            BoundedDequeBasedMessageQueueSemantics {

        private static final long serialVersionUID = -3799029649510617683L;

        private final LoggingAdapter log;

        private final int capacity;

        /**
         * Constructor.
         *
         * @param capacity the capacity of the queue
         * @param actorSystem the actor system
         */
        public ThingUpdaterMessageQueue(final int capacity, final ActorSystem actorSystem) {
            log = Logging.getLogger(actorSystem, ThingUpdaterMessageQueue.class);
            this.capacity = capacity;
        }

        @Override
        public Duration pushTimeOut() {
            return Duration.Zero();
        }

        @Override
        public void enqueue(final ActorRef receiver, final Envelope handle) {
            while (numberOfMessages() > (capacity - 1)) {
                log.warning(
                        "The capacity of the thing updater queue is exceeded, therefore the oldest message will be dropped");
                dequeue();
            }
            queue().add(handle);
        }

        @Override
        public Envelope dequeue() {
            return queue().poll();
        }

        @Override
        public int numberOfMessages() {
            return queue().size();
        }

        @Override
        public boolean hasMessages() {
            return !queue().isEmpty();
        }

        @Override
        public void cleanUp(final ActorRef owner, final MessageQueue deadLetters) {
            if (hasMessages()) {
                Envelope envelope = dequeue();
                while (envelope != null) {
                    deadLetters.enqueue(owner, envelope);
                    envelope = dequeue();
                }
            }
        }

        @Override
        public Deque<Envelope> queue() {
            return this;
        }

        @Override
        public void enqueueFirst(final ActorRef receiver, final Envelope handle) {
            // we can only enqueue messages at the beginning, if the capacity is not reached yet.
            if (numberOfMessages() < capacity) {
                queue().addFirst(handle);
            }
        }
    }
}
