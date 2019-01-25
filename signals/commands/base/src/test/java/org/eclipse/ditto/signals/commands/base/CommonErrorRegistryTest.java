/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.base;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.junit.Test;


/**
 * Unit test for {@link CommonErrorRegistry}.
 */
public class CommonErrorRegistryTest {

    final JsonParsableRegistry<DittoRuntimeException> underTest = CommonErrorRegistry.newInstance();


    @Test
    public void parseJsonError() {
        final DittoRuntimeException error = new DittoJsonException(
                JsonMissingFieldException.newBuilder().fieldName("thingId").build(),
                TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }
}
