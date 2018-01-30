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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

public class PolicyIdMissingExceptionTest {

    private static final String KNOWN_MESSAGE = "any Message";
    private static final String KNOWN_THING_ID = "org.eclipse.ditto:a.thing";
    private static final String KNOWN_DESCRIPTION = "any description";
    private static final URI KNOWN_HREF = URI.create("any://href");
    private static final String KNOWN_ERROR_CODE = PolicyIdMissingException.ERROR_CODE;
    private static final HttpStatusCode KNOWN_STATUS = HttpStatusCode.NOT_FOUND;


    private static final DittoHeaders KNOWN_HEADERS = DittoHeaders.newBuilder()
            .schemaVersion(JsonSchemaVersion.V_1)
            .correlationId("any")
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, KNOWN_STATUS.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, KNOWN_ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE, KNOWN_MESSAGE)
            .set(DittoRuntimeException.JsonFields.DESCRIPTION, KNOWN_DESCRIPTION)
            .set(DittoRuntimeException.JsonFields.HREF, KNOWN_HREF.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyIdMissingException.class, areImmutable());
    }

    @Test
    public void fromMessage() {
        final PolicyIdMissingException exception =  PolicyIdMissingException.fromMessage(KNOWN_MESSAGE, KNOWN_HEADERS);
        Assertions.assertThat(exception.getMessage()).isEqualTo(KNOWN_MESSAGE);
    }

    @Test
    public void fromJson() {
        final PolicyIdMissingException exception = PolicyIdMissingException.fromJson(KNOWN_JSON, KNOWN_HEADERS);
        Assertions.assertThat(exception.getMessage()).isEqualTo(KNOWN_MESSAGE);
    }

    @Test
    public void fromThingId() {
        final PolicyIdMissingException exception = PolicyIdMissingException.fromThingId(KNOWN_THING_ID, KNOWN_HEADERS);
        Assertions.assertThat(exception.getMessage()).contains(KNOWN_THING_ID);
    }
}