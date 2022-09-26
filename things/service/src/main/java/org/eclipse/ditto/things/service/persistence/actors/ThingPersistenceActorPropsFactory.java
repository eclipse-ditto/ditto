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
package org.eclipse.ditto.things.service.persistence.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory of thing-persistence-actor.
 */
@FunctionalInterface
public interface ThingPersistenceActorPropsFactory {

    /**
     * Create Props of thing-persistence-actor from thing ID and distributed-pub access for event publishing.
     *
     * @param thingId the thing ID.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the thing.
     * @param distributedPub the distributed-pub access.
     * @param searchShardRegionProxy the proxy of the shard region of search updaters.
     * @return Props of the thing-persistence-actor.
     */
    Props props(ThingId thingId, MongoReadJournal mongoReadJournal, DistributedPub<ThingEvent<?>> distributedPub,
            @Nullable ActorRef searchShardRegionProxy);
}
