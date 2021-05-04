/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Metadata}.
 */
public final class MetadataTest extends AbstractWithActorSystemTest {

    @Test
    public void testHashCodeAndEquals() {
        system = ActorSystem.create();
        final TestProbe probe1 = TestProbe.apply(system);
        final TestProbe probe2 = TestProbe.apply(system);
        EqualsVerifier.forClass(Metadata.class)
                .usingGetClass()
                .withPrefabValues(ActorRef.class, probe1.ref(), probe2.ref())
                .verify();
    }

}
