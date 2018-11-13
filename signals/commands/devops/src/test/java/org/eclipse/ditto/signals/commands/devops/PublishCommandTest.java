/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.devops;


import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link PublishCommand}.
 */
public final class PublishCommandTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PublishCommand.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PublishCommand.class, areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }
}
