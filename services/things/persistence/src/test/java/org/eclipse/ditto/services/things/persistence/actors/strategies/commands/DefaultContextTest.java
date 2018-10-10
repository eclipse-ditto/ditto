/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorPath;
import akka.actor.RootActorPath;
import akka.event.DiagnosticLoggingAdapter;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultContext}.
 */
public final class DefaultContextTest {

    private static final String THING_ID = "com.example.iot:myThing";
    private static final DiagnosticLoggingAdapter LOG = Mockito.mock(DiagnosticLoggingAdapter.class);
    private static final ThingSnapshotter SNAPSHOTTER = Mockito.mock(ThingSnapshotter.class);

    @Ignore("EqualsVerifier cannot cope with abstract method in ActorRef")
    @Test
    public void testHashCodeAndEquals() {
        final ActorPath red = new RootActorPath(null, "John Titor");
        final ActorPath blue = new RootActorPath(null, "");

        EqualsVerifier.forClass(DefaultContext.class)
                .usingGetClass()
                .withPrefabValues(ActorPath.class, red, blue)
                .verify();
    }

}
