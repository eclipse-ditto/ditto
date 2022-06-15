/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * An actor that create child actors on command. Used for connection testing.
 */
public final class ChildActorNannyActor extends AbstractActor {

    record StartChildActorConflictFree(CharSequence baseActorName, Props props) {}

    record StopThisActor() {}

    private final ChildActorNanny childActorNanny;

    @SuppressWarnings("unused") // called by reflection
    private ChildActorNannyActor() {
        childActorNanny =
                ChildActorNanny.newInstance(getContext(), DittoLoggerFactory.getDiagnosticLoggingAdapter(this));
    }

    @SuppressWarnings("unused") // called by reflection
    private ChildActorNannyActor(final ChildActorNanny childActorNanny) {
        this.childActorNanny = childActorNanny;
    }

    static Props props() {
        return Props.create(ChildActorNannyActor.class);
    }

    static Props propsForTest(final ChildActorNanny nannyToTest) {
        return Props.create(ChildActorNannyActor.class, nannyToTest);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(StartChildActorConflictFree.class, this::startChildActorConflictFree)
                .match(StopThisActor.class, this::stopThisActor)
                .build();
    }

    private void startChildActorConflictFree(final StartChildActorConflictFree cmd) {
        getSender().tell(childActorNanny.startChildActorConflictFree(cmd.baseActorName(), cmd.props()), getSelf());
    }

    private void stopThisActor(final StopThisActor trigger) {
        getContext().stop(getSelf());
    }
}
