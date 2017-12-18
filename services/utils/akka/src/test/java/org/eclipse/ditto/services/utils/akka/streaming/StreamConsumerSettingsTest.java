/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Test for {@link StreamConsumerSettings}.
 */
public class StreamConsumerSettingsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamConsumerSettings.class, areImmutable());
    }

    @Test
    public void equalsAndHashcode() throws Exception {
        EqualsVerifier.forClass(StreamConsumerSettings.class).verify();
    }

}