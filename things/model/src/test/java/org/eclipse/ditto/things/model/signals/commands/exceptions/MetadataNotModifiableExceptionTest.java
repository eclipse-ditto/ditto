/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;

import org.junit.Test;

/**
 * Unit test for {@link MetadataNotModifiableException}.
 */
public class MetadataNotModifiableExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.BAD_REQUEST.getCode())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, MetadataNotModifiableException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Thing.METADATA_NOT_MODIFIABLE_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Thing.METADATA_NOT_MODIFIABLE_EXCEPTION.getDescription().get())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Thing.METADATA_NOT_MODIFIABLE_EXCEPTION.getHref()
                            .map(URI::toString)
                            .orElse(null))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataNotModifiableException.class, areImmutable());
    }


    @Test
    public void checkErrorCodeWorks() {
        final DittoRuntimeException actual =
                GlobalErrorRegistry.getInstance()
                        .parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.Thing.METADATA_NOT_MODIFIABLE_EXCEPTION);
    }

}