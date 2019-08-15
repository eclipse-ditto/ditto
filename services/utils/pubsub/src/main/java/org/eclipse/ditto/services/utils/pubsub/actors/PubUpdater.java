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
package org.eclipse.ditto.services.utils.pubsub.actors;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.TopicBloomFiltersWriter;

import akka.actor.AbstractActorWithTimers;
import akka.actor.DeadLetter;
import akka.actor.Props;
import akka.cluster.ddata.Replicator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Remove remote subscriber on dead letter.
 */
public final class PubUpdater extends AbstractActorWithTimers {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "pubUpdater";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final TopicBloomFiltersWriter topicBloomFiltersWriter;

    private PubUpdater(final TopicBloomFiltersWriter topicBloomFiltersWriter) {
        this.topicBloomFiltersWriter = topicBloomFiltersWriter;
    }

    /**
     * Create Props object for this actor.
     *
     * @param topicBloomFiltersWriter writer of the distributed topic Bloom filters.
     */
    public static Props props(final TopicBloomFiltersWriter topicBloomFiltersWriter) {
        return Props.create(PubUpdater.class, topicBloomFiltersWriter);
    }

    @Override
    public Receive createReceive() {
        // TODO: test
        return ReceiveBuilder.create()
                .match(DeadLetter.class, this::deadLetter)
                .matchAny(this::logUnhandled)
                .build();
    }

    private void deadLetter(final DeadLetter deadLetter) {
        // publisher detected unreachable remote. remove it from local ORMap.
        log.info("Removing remote <{}> due to <{}>", deadLetter.recipient(), deadLetter);
        topicBloomFiltersWriter.removeSubscriber(deadLetter.recipient(), Replicator.writeLocal());
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }
}
