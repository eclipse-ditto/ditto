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
package org.eclipse.ditto.model.things;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

/**
 * Unit test for {@link ThingIdValidator}.
 */
public final class ThingIdValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingIdValidator.class, areImmutable());
    }

    @Test
    public void validationOfValidThingIdSucceeds() {
        final String thingId = "org.eclipse.ditto.test:myThing";

        ThingIdValidator.getInstance().accept(thingId, DittoHeaders.empty());
    }

    @Test
    public void validationOfInvalidThingIdThrowsException() {
        final String thingId = "myThing";

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingIdValidator.getInstance().accept(thingId, DittoHeaders.empty()))
                .withNoCause();
    }

}
