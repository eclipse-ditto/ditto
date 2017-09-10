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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.junit.Test;


/**
 * Unit test for {@link ThingNotDeletableException}.
 */
public final class ThingNotDeletableExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatusCode.NOT_FOUND.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, ThingNotDeletableException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION.getDescription().get())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION.getHref().toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingNotDeletableException.class, areImmutable());
    }


    @Test
    public void checkThingErrorCodeWorks() {
        final DittoRuntimeException actual =
                ThingErrorRegistry.newInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION);
    }

    @Test
    public void checkMessageV1() {
        final DittoRuntimeException createdException = ThingNotDeletableException.newBuilder("myThing")
                .dittoHeaders(DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build())
                .build();

        final String expectedMessage = "The Thing with ID 'myThing' could not be deleted as the requester "
                + "had insufficient permissions ( WRITE and ADMINISTRATE are required).";
        assertThat(createdException.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    public void checkMessageV2() {
        final DittoRuntimeException createdException = ThingNotDeletableException.newBuilder("myThing")
                .dittoHeaders(DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build())
                .build();

        final String expectedMessage = "The Thing with ID 'myThing' could not be deleted as the requester "
                + "had insufficient permissions ( WRITE on root resource is required).";
        assertThat(createdException.getMessage()).isEqualTo(expectedMessage);
    }

}
