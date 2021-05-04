/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.actors;

import static org.assertj.core.api.Assertions.assertThat;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.javadsl.TestKit;

/**
 * Control test case for {@link AbstractDittoRootActorTest}.
 * An actor that cannot restart successfully is defined on purpose
 * to check that the test case defined in the parent class can detect it.
 */
public final class DittoRootActorControlTest extends AbstractDittoRootActorTest {

    @Override
    protected String serviceName() {
        return "none";
    }

    @Override
    protected ActorSystem createActorSystem() {
        return ActorSystem.create();
    }

    @Override
    protected Props getRootActorProps(final ActorSystem system) {
        return Props.create(NonRestartableRootActor.class, NonRestartableRootActor::new);
    }

    @Override
    protected void assertRestartSuccess(final ActorRef underTest, final ActorRef failingChild, final TestKit testKit) {
        final Terminated terminated = testKit.expectMsgClass(Terminated.class);
        assertThat(terminated.getActor()).isEqualTo(underTest);
    }

    private static final class NonRestartableRootActor extends DittoRootActor {

        private final ActorRef child;

        private NonRestartableRootActor() {
            child = getContext().actorOf(Props.create(ThrowingActor.class, ThrowingActor::new), "child");
        }

        @Override
        public void preStart() {
            getContext().getSystem().actorOf(Props.create(ThrowingActor.class, ThrowingActor::new), "conflictingName");
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(RuntimeException.class, e -> child.forward(e, getContext()))
                    .build()
                    .orElse(super.createReceive());
        }
    }
}
