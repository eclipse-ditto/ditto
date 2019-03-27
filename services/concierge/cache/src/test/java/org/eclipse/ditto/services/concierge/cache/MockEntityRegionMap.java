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
package org.eclipse.ditto.services.concierge.cache;

import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;

/**
 * Creates mocked entity region maps for tests.
 */
public final class MockEntityRegionMap {

    public static EntityRegionMap uniform(final ActorRef actorRef) {
        return of(actorRef, actorRef);
    }

    public static EntityRegionMap of(final ActorRef thingsRegion, final ActorRef policiesRegion) {
        return EntityRegionMap.newBuilder()
                .put(ThingCommand.RESOURCE_TYPE, thingsRegion)
                .put(PolicyCommand.RESOURCE_TYPE, policiesRegion)
                .build();
    }
}
