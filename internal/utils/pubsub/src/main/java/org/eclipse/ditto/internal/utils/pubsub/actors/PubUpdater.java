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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Remove remote subscriber on cluster event {@link akka.cluster.ClusterEvent.MemberRemoved}.
 */
public final class PubUpdater extends AbstractActorWithTimers implements ClusterMemberRemovedAware {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "pubUpdater";

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final DDataWriter<ActorRef, ?> ddataWriter;

    @SuppressWarnings("unused")
    private PubUpdater(final DDataWriter<ActorRef, ?> ddataWriter) {
        this.ddataWriter = ddataWriter;
        subscribeForClusterMemberRemovedAware();
    }

    /**
     * Create Props object for this actor.
     *
     * @param topicsWriter writer of the topics distributed data.
     */
    public static Props props(final DDataWriter<ActorRef, ?> topicsWriter) {
        return Props.create(PubUpdater.class, topicsWriter);
    }

    @Override
    public Receive createReceive() {
        return receiveClusterMemberRemoved().orElse(ReceiveBuilder.create()
                .matchAny(this::logUnhandled)
                .build());
    }

    @Override
    public LoggingAdapter log() {
        return log;
    }

    @Override
    public DDataWriter<ActorRef, ?> getDDataWriter() {
        return ddataWriter;
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }
}
