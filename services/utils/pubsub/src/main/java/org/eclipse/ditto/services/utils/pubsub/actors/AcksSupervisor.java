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
                .match(AbstractUpdater.DeclareAckLabels.class, this::isAcksUpdaterAvailable, this::declareAckLabels)
                .match(AbstractUpdater.DeclareAckLabels.class, this::acksUpdaterUnavailable)
                .match(AbstractUpdater.RemoveSubscriber.class,
                        AbstractUpdater.RemoveSubscriber::isForAcknowledgementLabelDeclaration,
                        this::removeAcknowledgementLabelDeclaration)
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
    private void declareAckLabels(final AbstractUpdater.DeclareAckLabels request) {
        acksUpdater.tell(request.toSubscribe(), getSender());
    }

    private void updaterUnavailable(final SubUpdater.Request request) {
        log.error("AcksUpdater unavailable. Dropping <{}>", request);
        getSender().tell(new IllegalStateException("AcksUpdater not available"), getSelf());
    }

    private void acksUpdaterUnavailable(final AbstractUpdater.DeclareAckLabels request) {
        log.error("AcksUpdater unavailable. Failing <{}>", request);
        getSender().tell(new IllegalStateException("AcksUpdater not available"), getSelf());
    }

    private void removeAcknowledgementLabelDeclaration(final AbstractUpdater.RemoveSubscriber removeSubscriber) {
        if (isAcksUpdaterAvailable()) {
            acksUpdater.tell(removeSubscriber, getSender());
        } else {
            updaterUnavailable(removeSubscriber);
        }
    }

}
