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
package org.eclipse.ditto.things.api;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.mongo.SnapshotStreamingActor;

/**
 * Constants for the Things messaging.
 */
@Immutable
public final class ThingsMessagingConstants {

    /**
     * Path of the root actor.
     */
    @SuppressWarnings("squid:S1075")
    private static final String ROOT_ACTOR_PATH = "/user/thingsRoot/";

    /**
     * Name of the actor created by ThingsPersistenceStreamingActorCreator.
     */
    public static final String THINGS_PERSISTENCE_STREAMING_ACTOR_NAME = SnapshotStreamingActor.ACTOR_NAME;

    /**
     * Name of ThingsAggregatorActor. To query this actor use the the {@link #THINGS_AGGREGATOR_ACTOR_PATH actor path}.
     */
    public static final String THINGS_AGGREGATOR_ACTOR_NAME = "aggregator";

    /**
     * Path of the actor is used for aggregating things handling {@code RetrieveThings} command.
     */
    public static final String THINGS_AGGREGATOR_ACTOR_PATH = ROOT_ACTOR_PATH + THINGS_AGGREGATOR_ACTOR_NAME;

    /**
     * Name of the shard region for Thing entities.
     */
    public static final String SHARD_REGION = "thing";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "things";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsMessagingConstants() {
        // no-op
    }
}
