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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor responsible for enforcing that {@link org.eclipse.ditto.signals.commands.base.Command}s are checked if they are
 * allowed to be processed by the responsible persistence actor. <ul> <li>A {@link PolicyCommand} will be proxied to the
 * policies shard region.</li> <li>A {@link ThingCommand} will be proxied to the things shard region.</li> <li>A {@link
 * org.eclipse.ditto.signals.commands.messages.MessageCommand} will be proxied via distributed pub-sub.</li> </ul> <p>
 * For each {@code policyId} an instance of this Actor is created which caches the {@code PolicyEnforcer} used to
 * determine the permissions. </p>
 */
public final class PolicyEnforcerActor extends AbstractThingPolicyEnforcerActor {

    private PolicyEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion,
            final ActorRef policyCacheFacade,
            final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout) {
        super(pubSubMediator, policiesShardRegion, thingsShardRegion, policyCacheFacade, cacheInterval, askTimeout,
                whitelistedJsonFields());
    }

    private static Map<String, JsonFieldSelector> whitelistedJsonFields() {
        final Map<String, JsonFieldSelector> whitelistedJsonFields = new HashMap<>();
        whitelistedJsonFields.put(ThingCommand.RESOURCE_TYPE, JsonFactory.newFieldSelector(Thing.JsonFields.ID));
        whitelistedJsonFields.put(PolicyCommand.RESOURCE_TYPE, JsonFactory.newFieldSelector(Policy.JsonFields.ID));
        return whitelistedJsonFields;
    }

    /**
     * Creates Akka configuration object Props for this PolicyEnforcerActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param policiesShardRegion the Actor ref of the ShardRegion of {@code Policies}.
     * @param thingsShardRegion the Actor ref of the ShardRegion of {@code Things}.
     * @param policyCacheFacade the Actor ref to the distributed cache facade for policies.
     * @param cacheInterval the interval of how long the created PolicyEnforcerActor should be hold in cache w/o any
     * activity happening.
     * @param askTimeout the internal timeout when retrieving the {@link Policy} or when waiting for a {@link
     * CommandResponse}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion,
            final ActorRef policyCacheFacade,
            final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout) {
        return Props.create(PolicyEnforcerActor.class, new Creator<PolicyEnforcerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PolicyEnforcerActor create() throws Exception {
                return new PolicyEnforcerActor(pubSubMediator, policiesShardRegion, thingsShardRegion,
                        policyCacheFacade, cacheInterval, askTimeout);
            }
        });
    }

    @Override
    protected void addEnforcingBehaviour(final ReceiveBuilder receiveBuilder) {
        addThingEnforcingBehaviour(receiveBuilder);
    }

}
