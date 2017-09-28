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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingCommandToModifyExceptionRegistry}.
 */
public final class ThingCommandToModifyExceptionRegistryTest {

    private ThingCommandToModifyExceptionRegistry registryUnderTest;

    @Before
    public void setup() {
        registryUnderTest = ThingCommandToModifyExceptionRegistry.getInstance();
    }

    @Test
    public void mapModifyThingToThingNotModifiable() {
        final DeleteAttribute modifyThing = DeleteAttribute.of(":thingId", JsonPointer.of("abc"), DittoHeaders.empty());
        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(modifyThing);
        final DittoRuntimeException expectedException = AttributeNotModifiableException.newBuilder(":thingId",
                JsonPointer.of("abc")).build();

        assertThat(mappedException).isEqualTo(expectedException);
    }

}
