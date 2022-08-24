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
package org.eclipse.ditto.things.model;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

public final class PolicyIdMissingExceptionTest {

    private static final String KNOWN_MESSAGE = "any Message";
    private static final ThingId KNOWN_THING_ID = ThingId.of("org.eclipse.ditto", "a.thing");
    private static final String KNOWN_DESCRIPTION = "any description";
    private static final URI KNOWN_HREF = URI.create("any://href");
    private static final String KNOWN_ERROR_CODE = PolicyIdMissingException.ERROR_CODE;
    private static final HttpStatus KNOWN_STATUS = HttpStatus.NOT_FOUND;

    private static final DittoHeaders KNOWN_HEADERS = DittoHeaders.newBuilder()
            .correlationId("any")
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, KNOWN_STATUS.getCode())
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
    public void fromJson() {
        final PolicyIdMissingException exception = PolicyIdMissingException.fromJson(KNOWN_JSON, KNOWN_HEADERS);
        Assertions.assertThat(exception.getMessage()).isEqualTo(KNOWN_MESSAGE);
    }

    @Test
    public void fromThingIdOnUpdate() {
        final PolicyIdMissingException exception =
                PolicyIdMissingException.fromThingIdOnUpdate(KNOWN_THING_ID, KNOWN_HEADERS);
        Assertions.assertThat(exception.getMessage()).contains(KNOWN_THING_ID.toString());
    }

    @Test
    public void fromThingIdOnCreate() {
        final PolicyIdMissingException exception = PolicyIdMissingException.fromThingIdOnCreate(KNOWN_THING_ID,
                KNOWN_HEADERS);
        Assertions.assertThat(exception.getMessage()).contains(KNOWN_THING_ID.toString());
    }

}
