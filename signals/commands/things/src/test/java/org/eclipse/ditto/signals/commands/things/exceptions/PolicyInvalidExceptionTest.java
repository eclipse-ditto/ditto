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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.junit.Test;

/**
 * Unit test for {@link PolicyInvalidException}.
 */
public class PolicyInvalidExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, PolicyInvalidException.STATUS_CODE.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, PolicyInvalidException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Thing.POLICY_INVALID_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Thing.POLICY_INVALID_EXCEPTION.getDescription().orElse(null),
                    JsonField.isValueNonNull())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Thing.POLICY_INVALID_EXCEPTION.getHref()
                            .map(URI::toString)
                            .orElse(null),
                    JsonField.isValueNonNull())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyInvalidException.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = TestConstants.Thing.POLICY_INVALID_EXCEPTION.toJson();

        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final PolicyInvalidException underTest =
                PolicyInvalidException.fromJson(KNOWN_JSON, DittoHeaders.empty());

        DittoBaseAssertions.assertThat(underTest).isEqualTo(TestConstants.Thing.POLICY_INVALID_EXCEPTION);
    }


    @Test
    public void copy() {
        final DittoRuntimeException copy =
                DittoRuntimeException.newBuilder(TestConstants.Thing.POLICY_INVALID_EXCEPTION).build();

        assertThat(copy).isEqualTo(TestConstants.Thing.POLICY_INVALID_EXCEPTION);
    }


    @Test
    public void checkGetters() {
        final String expectedMessage =
                "The Policy specified for the Thing with ID '" + TestConstants.Thing.THING_ID + "' is invalid.";
        final String expectedDescription = "It must contain at least one Subject with the following permission(s): " +
                "'[" + String.join(", ", TestConstants.Thing.REQUIRED_THING_PERMISSIONS) + "]'!";

        DittoBaseAssertions.assertThat(TestConstants.Thing.POLICY_INVALID_EXCEPTION)
                .hasMessage(expectedMessage)
                .hasDescription(expectedDescription)
                .hasErrorCode(PolicyInvalidException.ERROR_CODE)
                .hasStatusCode(PolicyInvalidException.STATUS_CODE);
    }

    @Test
    public void checkThingErrorCodeWorks() {
        final DittoRuntimeException actual =
                ThingErrorRegistry.newInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.Thing.POLICY_INVALID_EXCEPTION);
    }

}
