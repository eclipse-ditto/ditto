/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import org.junit.Test;

import akka.actor.ActorPath;
import akka.actor.RootActorPath;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultContext}.
 */
public final class DefaultContextTest {

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
