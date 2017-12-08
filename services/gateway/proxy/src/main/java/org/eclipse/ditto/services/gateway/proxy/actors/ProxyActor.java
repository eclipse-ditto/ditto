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
package org.eclipse.ditto.services.gateway.proxy.actors;

import org.eclipse.ditto.signals.commands.base.Command;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;

/**
 * Actor which delegates {@link Command}s to the appropriate receivers in the cluster.
 */
public final class ProxyActor extends AbstractThingProxyActor {

    private ProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef thingEnforcerLookup,
            final ActorRef thingCacheFacade) {
        super(pubSubMediator, devOpsCommandsActor, aclEnforcerShardRegion, policyEnforcerShardRegion,
                thingEnforcerLookup, thingCacheFacade);
    }

    /**
     * Creates Akka configuration object Props for this ProxyActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param devOpsCommandsActor the Actor ref to the local DevOpsCommandsActor.
     * @param aclEnforcerShardRegion the Actor ref of the acl enforcer shard region.
     * @param policyEnforcerShardRegion the Actor ref of the policy enforcer shard region.
     * @param thingEnforcerLookup the Actor ref to the thing enforcer lookup actor.
     * @param thingCacheFacade the Actor ref to the thing cache facade actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef thingEnforcerLookup,
            final ActorRef thingCacheFacade) {
        return Props.create(ProxyActor.class, new Creator<ProxyActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ProxyActor create() throws Exception {
                return new ProxyActor(pubSubMediator, devOpsCommandsActor, aclEnforcerShardRegion,
                        policyEnforcerShardRegion, thingEnforcerLookup, thingCacheFacade);
            }
        });
    }

}
