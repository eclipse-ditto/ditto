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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link TooManyRequestsException}.
 */
public class TooManyRequestsExceptionTest {

    final TooManyRequestsException UNDER_TEST = TooManyRequestsException.newBuilder()
            .retryAfter(Duration.ofHours(1L))
            .build();

    final JsonObject KNOWN_JSON = JsonFactory.newObject("{\n" +
            "  \"status\": 429,\n" +
            "  \"error\": \"too.many.requests\",\n" +
            "  \"message\": \"You made too many requests.\",\n" +
            "  \"description\": \"Try again soon.\"\n" +
            "}");

    @Test
    public void assertImmutability() {
        assertInstancesOf(TooManyRequestsException.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = UNDER_TEST.toJson();
        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final TooManyRequestsException deserialized =
                TooManyRequestsException.fromJson(KNOWN_JSON, DittoHeaders.empty());
        assertThat(deserialized).isEqualTo(UNDER_TEST);
    }

    @Test
    public void retryAfterHeaderIsSet() {
        assertThat(UNDER_TEST.getDittoHeaders()).containsEntry(TooManyRequestsException.RETRY_AFTER, "3600");
    }

}
