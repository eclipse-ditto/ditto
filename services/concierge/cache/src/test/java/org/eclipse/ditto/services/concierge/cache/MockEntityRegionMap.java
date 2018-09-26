/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
