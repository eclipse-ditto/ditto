/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Test for {@link CommandHeaderInvalidException}.
 */
public final class CommandHeaderInvalidExceptionTest {

    private static final String KNOWN_INVALID_HEADER_KEY = "invalid-key";
    private static final String KNOWN_DESCRIPTION = "This is a useful description on how to mitigate the error";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.BAD_REQUEST.getCode())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, CommandHeaderInvalidException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    MessageFormat.format(CommandHeaderInvalidException.MESSAGE_TEMPLATE, KNOWN_INVALID_HEADER_KEY))
            .set(DittoRuntimeException.JsonFields.DESCRIPTION, KNOWN_DESCRIPTION)
            .set(DittoRuntimeException.JsonFields.HREF, null)
            .build();

    private static final CommandHeaderInvalidException COMMAND_HEADER_INVALID_EXCEPTION =
            CommandHeaderInvalidException.newBuilder(KNOWN_INVALID_HEADER_KEY)
                    .description(KNOWN_DESCRIPTION)
                    .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(CommandHeaderInvalidException.class, areImmutable());
    }

    @Test
    public void checkAttributeErrorCodeWorks() {
        final DittoRuntimeException actual =
                GlobalErrorRegistry.getInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        DittoBaseAssertions.assertThat(actual).isEqualTo(COMMAND_HEADER_INVALID_EXCEPTION);
    }

}
