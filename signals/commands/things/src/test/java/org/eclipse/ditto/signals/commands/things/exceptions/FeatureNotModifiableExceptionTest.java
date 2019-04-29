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
package org.eclipse.ditto.signals.commands.things.exceptions;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.junit.Test;

/**
 * Unit test for {@link FeatureNotModifiableException}.
 */
public class FeatureNotModifiableExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatusCode.NOT_FOUND.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, FeatureNotModifiableException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Feature.FEATURE_NOT_MODIFIABLE_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Feature.FEATURE_NOT_MODIFIABLE_EXCEPTION.getDescription().get())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Feature.FEATURE_NOT_MODIFIABLE_EXCEPTION.getHref()
                            .map(URI::toString).orElse(null))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureNotModifiableException.class, areImmutable());
    }


    @Test
    public void checkFeatureErrorCodeWorks() {
        final DittoRuntimeException actual =
                GlobalErrorRegistry.getInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.Feature.FEATURE_NOT_MODIFIABLE_EXCEPTION);
    }

}
