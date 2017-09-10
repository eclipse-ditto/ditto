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
package org.eclipse.ditto.services.gateway.proxy.actors.handlers;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Creates a handler actor for Thing commands.
 */
@FunctionalInterface
public interface ThingHandlerCreator {

    /**
     * Creates props for handlers of Thing commands.
     *
     * @param enforcerShard Reference to the shard region actor containing the enforcer for this command, or null if it
     * does not exist.
     * @param enforcerId ID of the enforcer actor within the shard for this command, or null if it does not exist.
     * @param aclEnforcerShard The shard region of ACL enforcer actors.
     * @param policyEnforcerShard The shard region of policy enforcer actors.
     * @return a function creating this actor from enforcer shard region and policies shard region.
     */
    Props props(final ActorRef enforcerShard, final String enforcerId, final ActorRef aclEnforcerShard,
            final ActorRef policyEnforcerShard);
}
