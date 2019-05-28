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
package org.eclipse.ditto.services.concierge.actors.cleanup;

import java.time.Duration;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Cluster singleton actor to assembles the stream to cleanup old snapshots and events and report on its status.
 *
 * <pre>{@code
 *
 *    Report failures with timestamp
 *                 ^
 *                 |
 *                 |
 *                 |
 *             +---+---------------+
 *             |All persistence IDs+-------------------------------+
 *             +-------------------+                               |
 *                                                                 v            +------------------+
 *                                                              +--+--+         |Forward to        |
 *                                                              |Merge+-------->+PersistenceActor  |
 *                                                              +--+--+         |with back pressure|
 *                                                                 ^            +--------+---------+
 *                                                                 |                     |
 *                                                                 |                     |
 *             +-------+         +---------------+                 |                     |
 *             |Metrics+-------->+Credit decision+-----------------+                     |
 *             +-------+         +------+--------+                                       |
 *                                      |                                                |
 *                                      |                                                |
 *                                      v                                                v
 *                      Report metrics and credit decision                     Report round trip time
 *
 *
 * }</pre>
 *
 */
public final class EventSnapshotCleanupCoordinator extends AbstractActor {

    // TODO: turn these into configurations.
    static final Duration HEALTH_CHECK_INTERVAL = Duration.ofMinutes(1L);
    static final Duration METRICS_REPORT_INTERVAL = Duration.ofSeconds(5L);

    @Override
    public Receive createReceive() {
        // TODO
        return ReceiveBuilder.create()
                .build();
    }
}
