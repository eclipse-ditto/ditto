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
package org.eclipse.ditto.services.things.persistence.actors;

import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
     * @param distributedPub the distributed-pub access.
     * @return Props of the thing-persistence-actor.
     */
    Props props(String thingId, DistributedPub<ThingEvent> distributedPub);
}
