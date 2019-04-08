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
    public void validationOfValidThingIdFailsWithEmptyName() {
        final String thingIdWithoutThingName = "org.eclipse.ditto.test:";

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingIdValidator.getInstance().accept(thingIdWithoutThingName, DittoHeaders.empty()))
                .withNoCause();
    }

    @Test
    public void validationOfValidThingIdSucceedsWithEmptyNamespace() {
        final String thingIdWithoutThingName = ":test";

        ThingIdValidator.getInstance().accept(thingIdWithoutThingName, DittoHeaders.empty());
    }

    @Test
    public void validationOfValidThingIdFailsWithEmptyNamespaceAndName() {
        final String thingIdWithoutThingName = ":";

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingIdValidator.getInstance().accept(thingIdWithoutThingName, DittoHeaders.empty()))
                .withNoCause();
    }

    @Test
    public void validationOfValidThingIdFailsWithOnlyNamespace() {
        final String thingIdWithoutThingName = "org.eclipse.ditto.test";

        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> ThingIdValidator.getInstance().accept(thingIdWithoutThingName, DittoHeaders.empty()))
                .withNoCause();
    }
}
