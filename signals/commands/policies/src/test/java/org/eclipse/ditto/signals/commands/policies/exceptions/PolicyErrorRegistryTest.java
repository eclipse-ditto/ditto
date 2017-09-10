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
package org.eclipse.ditto.signals.commands.policies.exceptions;


import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

/**
 * Unit test for {@link PolicyErrorRegistry}.
 */
public class PolicyErrorRegistryTest {

    final JsonParsableRegistry<DittoRuntimeException> underTest = PolicyErrorRegistry.newInstance();


    @Test
    public void parsePolicyError() {
        final PolicyIdInvalidException error = PolicyIdInvalidException.newBuilder(TestConstants.Policy.POLICY_ID)
                .dittoHeaders(TestConstants.DITTO_HEADERS).build();
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
                        .fieldSelector(Policy.JsonFields.ID.getPointer().toString())
                        .build(), TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }

}
