/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.pubsub.api.Request;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of AcksUpdater.
 */
public final class AcksSupervisor extends AbstractPubSubSupervisor {

    private final Address selfAddress;
    private final DData<Address, String, LiteralUpdate> acksDData;

    @Nullable private ActorRef acksUpdater;

    @SuppressWarnings("unused")
    private AcksSupervisor(final DData<Address, String, LiteralUpdate> acksDData) {
        super();
        this.acksDData = acksDData;
        selfAddress = Cluster.get(getContext().getSystem()).selfAddress();
    }

    /**
     * Create Props object for this actor.
     *
     * @param acksDData access to the distributed data of acknowledgement labels.
     * @return the Props object.
     */
    public static Props props(final DData<Address, String, LiteralUpdate> acksDData) {
        return Props.create(AcksSupervisor.class, acksDData);
    }

    @Override
    protected Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
                .match(Request.class, this::isAcksUpdaterAvailable, this::forwardRequest)
                .match(Request.class, this::acksUpdaterUnavailable)
                .build();
    }

    @Override
    protected void onChildFailure() {
        // if this ever happens, consider adding a recovery mechanism in SubUpdater.postStop.
        acksUpdater = null;
        log.error("All local subscriptions lost.");
    }

    @Override
    protected void startChildren() {
        final Props acksUpdaterProps = AcksUpdater.props(config, selfAddress, acksDData);
        acksUpdater = startChild(acksUpdaterProps, AcksUpdater.ACTOR_NAME_PREFIX);
    }

    private boolean isAcksUpdaterAvailable() {
        return acksUpdater != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void forwardRequest(final Request request) {
        acksUpdater.tell(request, getSender());
    }

    private void acksUpdaterUnavailable(final Request request) {
        log.error("AcksUpdater unavailable. Failing <{}>", request);
        getSender().tell(new IllegalStateException("AcksUpdater not available"), getSelf());
    }

}
