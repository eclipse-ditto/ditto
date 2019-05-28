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
                RetrieveAttribute.of("org.eclipse.ditto:thingId", JsonFactory.newPointer("abc"), DittoHeaders.empty());
        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(retrieveAttribute);
        final DittoRuntimeException expectedException =
                AttributeNotAccessibleException.newBuilder("org.eclipse.ditto:thingId", JsonPointer.of("abc")).build();
        assertThat(mappedException).isEqualTo(expectedException);
    }

}
