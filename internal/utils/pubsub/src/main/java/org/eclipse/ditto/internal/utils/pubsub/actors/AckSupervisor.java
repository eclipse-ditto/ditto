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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.pubsub.api.AckRequest;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of AckUpdater.
 */
public final class AckSupervisor extends AbstractPubSubSupervisor {

    private final Address selfAddress;
    private final DData<Address, String, LiteralUpdate> ackDData;

    @Nullable private ActorRef ackUpdater;

    @SuppressWarnings("unused")
    private AckSupervisor(final DData<Address, String, LiteralUpdate> ackDData) {
        super();
        this.ackDData = ackDData;
        selfAddress = Cluster.get(getContext().getSystem()).selfAddress();
    }

    /**
     * Create Props object for this actor.
     *
     * @param ackDData access to the distributed data of acknowledgement labels.
     * @return the Props object.
     */
    public static Props props(final DData<Address, String, LiteralUpdate> ackDData) {
        return Props.create(AckSupervisor.class, ackDData);
    }

    @Override
    protected Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
                .match(AckRequest.class, this::isAckUpdaterAvailable, this::forwardRequest)
                .match(AckRequest.class, this::ackUpdaterUnavailable)
                .match(Terminated.class, this::ackUpdaterTerminated)
                .build();
    }

    @Override
    protected void onChildFailure(final ActorRef failingChild) {
        // if this ever happens, consider adding a recovery mechanism in SubUpdater.postStop.
        ackUpdater = null;
        log.error("All local declared acknowledgement labels lost.");
        ackDData.getWriter().removeAddress(selfAddress, ClusterMemberRemovedAware.writeLocal());
    }

    @Override
    protected void startChildren() {
        final Props acksUpdaterProps = AckUpdater.props(config, selfAddress, ackDData);
        ackUpdater = startChild(acksUpdaterProps, AckUpdater.ACTOR_NAME_PREFIX);
    }

    private boolean isAckUpdaterAvailable() {
        return ackUpdater != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void forwardRequest(final AckRequest request) {
        ackUpdater.tell(request, getSender());
    }

    private void ackUpdaterUnavailable(final AckRequest request) {
        log.error("AckUpdater unavailable. Failing <{}>", request);
        getSender().tell(ActorEvent.ACK_UPDATER_NOT_AVAILABLE, getSelf());
    }

    private void ackUpdaterTerminated(final Terminated terminated) {
        if (terminated.getActor().equals(ackUpdater)) {
            onChildFailure(ackUpdater);
            scheduleRestartChildren();
        }
    }

}
