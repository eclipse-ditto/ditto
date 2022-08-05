/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.exceptions;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException}.
 */
public final class DittoHeadersTooLargeExceptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoHeadersTooLargeException.class, areImmutable());
    }

    @Test
    public void buildForSize() {
        final DittoHeadersTooLargeException underTest = DittoHeadersTooLargeException.newSizeLimitBuilder(5).build();
        final JsonObject json = underTest.toJson();

        assertThat(json).isEqualTo(JsonFactory.newObject("{" +
                "\"status\":431," +
                "\"error\":\"headers.too.large\"," +
                "\"message\":\"The headers are too large.\"," +
                "\"description\":\"The number of bytes exceeded the maximum allowed value <5>!\"" +
                "}"));

        final DittoHeadersTooLargeException deserialized =
                DittoHeadersTooLargeException.fromJson(json, DittoHeaders.empty());

        assertThat(deserialized).isEqualTo(underTest);
    }

    @Test
    public void buildForAuthSubjectsCount() {
        final DittoHeadersTooLargeException underTest =
                DittoHeadersTooLargeException.newAuthSubjectsLimitBuilder(1, 2).build();
        final JsonObject json = underTest.toJson();

        assertThat(json).isEqualTo(JsonFactory.newObject("{" +
                "\"status\":431," +
                "\"error\":\"headers.too.large\"," +
                "\"message\":\"The headers are too large.\"," +
                "\"description\":\"The number of authorization subjects <1> exceeded the maximum allowed value <2>.\"" +
                "}"));

        final DittoHeadersTooLargeException deserialized =
                DittoHeadersTooLargeException.fromJson(json, DittoHeaders.empty());

        assertThat(deserialized).isEqualTo(underTest);
    }

}
