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
package org.eclipse.ditto.services.base.actors;

import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

public abstract class DittoRootActor extends AbstractActor {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(StartChildActor.class, this::startChildActor)
                .build();
    }

    private void startChildActor(final StartChildActor startChildActor) {
        log.info("Starting child actor <{}>.", startChildActor.actorName);
        getContext().actorOf(startChildActor.props, startChildActor.actorName);
    }

    public static class StartChildActor {

        private final Props props;
        private final String actorName;

        public StartChildActor(final Props props, final String actorName) {
            this.props = props;
            this.actorName = actorName;
        }

    }

}
