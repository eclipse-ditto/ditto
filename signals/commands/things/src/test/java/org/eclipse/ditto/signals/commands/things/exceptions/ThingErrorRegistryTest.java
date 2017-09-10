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

import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.junit.Test;


/**
 * Unit test for {@link ThingErrorRegistry}.
 */
public class ThingErrorRegistryTest {

    final JsonParsableRegistry<DittoRuntimeException> underTest = ThingErrorRegistry.newInstance();


    @Test
    public void parseThingError() {
        final ThingIdInvalidException error =
                ThingIdInvalidException.newBuilder(TestConstants.Thing.THING_ID)
                        .dittoHeaders(TestConstants.DITTO_HEADERS)
                        .build();
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }

    /**
     * Test that JSON exceptions are parsable, too.
     */
    @Test
    public void parseJsonError() {
        final DittoRuntimeException error = new DittoJsonException(
                JsonFieldSelectorInvalidException.newBuilder()
                        .fieldSelector(Thing.JsonFields.ID.getPointer().toString())
                        .build(), TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }

}
