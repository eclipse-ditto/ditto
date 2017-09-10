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

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

/**
 * This Actor handles {@link ModifyPolicy} commands. It forwards requests made to API version 1 and
 * intersects requests to API version 2 to transform the {@link ModifyPolicy} to a {@link CreatePolicy}.<br>
 * This actor will terminate after it has handled the request.
 */
public class ModifyPolicyHandlerActor extends AbstractActor {

    private static final Timeout ASK_TIMEOUT = new Timeout(Duration.create(20000, TimeUnit.MILLISECONDS));

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef policyEnforcerShardRegion;

    private ActorRef requester;

    private ModifyPolicyHandlerActor(final ActorRef policyEnforcerShardRegion) {
        this.policyEnforcerShardRegion = policyEnforcerShardRegion;
    }

    /**
     * Creates Akka configuration object Props for this ModifyPolicyHandlerActor.
     *
     * @param policyEnforcerShardRegion the Actor ref of the {@code PolicyEnforcer} shard region.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef policyEnforcerShardRegion) {
        return Props.create(ModifyPolicyHandlerActor.class, new Creator<ModifyPolicyHandlerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ModifyPolicyHandlerActor create() throws Exception {
                return new ModifyPolicyHandlerActor(policyEnforcerShardRegion);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ModifyPolicy.class, modifyPolicy ->
                {
                    requester = getSender();

                    final CreatePolicy createPolicy = CreatePolicy.of(modifyPolicy.getPolicy(),
                            modifyPolicy.getDittoHeaders());

                    policyEnforcerShardRegion.tell(createPolicy, getSelf());
                    becomeCreatePolicyResponseAwaiting(modifyPolicy);
                })
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();
    }

    private void becomeCreatePolicyResponseAwaiting(final ModifyPolicy modifyPolicy) {
        final Cancellable timeout = getContext().system().scheduler().scheduleOnce(ASK_TIMEOUT.duration(), getSelf(),
                new AskTimeoutException("The PoliciesActor did not respond within the specified time frame"),
                getContext().dispatcher(), null);

        getContext().become(ReceiveBuilder.create()
                .match(CreatePolicyResponse.class, response -> {
                    // in this case the Policy does not exist and is now created
                    timeout.cancel();
                    requester.tell(response, getSelf());
                    getContext().stop(getSelf());
                })
                .match(PolicyConflictException.class, policyConflict -> {
                    // the Policy already exists
                    timeout.cancel();
                    policyEnforcerShardRegion.tell(modifyPolicy, requester);
                    getContext().stop(getSelf());
                })
                .match(DittoRuntimeException.class, cre -> {
                    timeout.cancel();
                    LogUtil.enhanceLogWithCorrelationId(log, cre.getDittoHeaders().getCorrelationId());
                    log.info("Got an unexpected DittoRuntimeException while trying to modify a Policy: {}", cre);
                    requester.tell(cre, getSelf());
                    getContext().stop(getSelf());
                })
                .match(AskTimeoutException.class, e -> {
                    log.error("Timeout exception while trying to create the Policy");
                    requester.tell(PolicyUnavailableException.newBuilder(modifyPolicy.getId())
                                    .dittoHeaders(modifyPolicy.getDittoHeaders())
                                    .build(),
                            getSelf());
                    getContext().stop(getSelf());
                })
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build());
    }

}
