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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.junit.Test;


/**
 * Unit test for {@link ThingNotDeletableException}.
 */
public final class ThingNotDeletableExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.NOT_FOUND.getCode())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, ThingNotDeletableException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION.getDescription().get())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION.getHref()
                            .map(URI::toString).orElse(null))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingNotDeletableException.class, areImmutable());
    }


    @Test
    public void checkThingErrorCodeWorks() {
        final DittoRuntimeException actual =
                GlobalErrorRegistry.getInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.Thing.THING_NOT_DELETABLE_EXCEPTION);
    }

    @Test
    public void checkMessageV2() {
        final ThingId thingId = ThingId.of("foo", "bar");
        final DittoRuntimeException createdException = ThingNotDeletableException.newBuilder(thingId)
                .dittoHeaders(DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build())
                .build();

        final String expectedMessage = "The Thing with ID '"+thingId.toString()+"' could not be deleted as the " +
                "requester had insufficient permissions ( WRITE on root resource is required).";
        assertThat(createdException.getMessage()).isEqualTo(expectedMessage);
    }

}
