/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.api;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Timeseries service messaging — service name, Pekko cluster role, root-actor
 * path.
 *
 * @since 4.0.0
 */
@Immutable
public final class TimeseriesMessagingConstants {

    /**
     * Service name as used for HOCON {@code ditto.service-name} and logging.
     */
    public static final String SERVICE_NAME = "timeseries";

    /**
     * Name of the Pekko cluster role for Timeseries-service nodes.
     */
    public static final String CLUSTER_ROLE = "timeseries";

    @SuppressWarnings("squid:S1075")
    private static final String USER_PATH = "/user";

    /**
     * Name of the Timeseries root actor.
     */
    public static final String ROOT_ACTOR_NAME = "timeseriesRoot";

    /**
     * Path of the Timeseries root actor.
     */
    public static final String ROOT_ACTOR_PATH = USER_PATH + "/" + ROOT_ACTOR_NAME;

    /**
     * Name of the Pekko cluster shard region that hosts the per-Thing
     * {@code TimeseriesIngestActor} entities on Timeseries-service nodes. Each entity is a
     * Pekko-persistent actor whose journal lives in the {@code timeseries_journal} MongoDB
     * collection — same backbone as {@code ThingPersistenceActor}'s {@code things_journal}.
     * <p>
     * The same entity handles both the ingest write path
     * ({@link org.eclipse.ditto.timeseries.api.commands.IngestDataPoints}) and the
     * {@link org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries} read path —
     * mirroring how {@code ThingPersistenceActor} services every command for its Thing. That
     * lets the edge command forwarder route both with
     * {@code askWithRetryCommandForwarder} against this shard region (same shape as
     * {@code forwardToThings} / {@code forwardToPolicies}).
     */
    public static final String SHARD_REGION = "timeseries-ingest";

    /**
     * Name of the per-node Timeseries ingest-publisher actor running on each Things-service
     * node. Watches thing events flowing through the local {@code ThingPersistenceActor},
     * looks up the WoT {@code ditto:timeseries} annotation on touched feature properties,
     * builds {@link org.eclipse.ditto.timeseries.model.TimeseriesDataPoint}s and asks the
     * timeseries shard region with bounded retries until the entity acks. The entity's
     * journal closes the durability gap on the receiving side; the publisher's retry loop
     * handles transient delivery failures (shard rebalance, network blip).
     */
    public static final String INGEST_PUBLISHER_ACTOR_NAME = "timeseriesIngestPublisher";

    private TimeseriesMessagingConstants() {
        // no-op
    }
}
