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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator}.
 */
public class ConnectionValidatorTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(ConnectionValidator.class,
                areImmutable(),
                // mutability-detector cannot detect that maps built from stream connectors are safely copied.
                assumingFields("specMap").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }
}
