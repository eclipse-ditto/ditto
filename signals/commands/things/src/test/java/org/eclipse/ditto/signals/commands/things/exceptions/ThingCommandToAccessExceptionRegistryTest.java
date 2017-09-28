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
package org.eclipse.ditto.signals.commands.things.exceptions;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingCommandToAccessExceptionRegistry}.
 */
public final class ThingCommandToAccessExceptionRegistryTest {

    private ThingCommandToAccessExceptionRegistry registryUnderTest;

    @Before
    public void setup() {
        registryUnderTest = ThingCommandToAccessExceptionRegistry.getInstance();
    }

    @Test
    public void mapRetrieveAttributeToAttributeNotAccessible() {
        final RetrieveAttribute retrieveAttribute =
                RetrieveAttribute.of(":thingId", JsonFactory.newPointer("abc"), DittoHeaders.empty());
        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(retrieveAttribute);
        final DittoRuntimeException expectedException =
                AttributeNotAccessibleException.newBuilder(":thingId", JsonPointer.of("abc")).build();
        assertThat(mappedException).isEqualTo(expectedException);
    }

}
