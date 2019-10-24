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
package org.eclipse.ditto.model.connectivity.replies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.model.connectivity.replies.ImmutableReplyTarget}.
 */
public class ImmutableReplyTargetTest {

    private static final String ADDRESS = "amqp/target1";

    private static final JsonObject MAPPING = JsonFactory.newObjectBuilder()
            .set("correlation-id", "{{ header:message-id }}")
            .set("thing-id", "{{ header:device_id }}")
            .set("eclipse", "ditto")
            .build();

    public static final ReplyTarget REPLY_TARGET =
            ReplyTarget.newBuilder()
                    .address(ADDRESS)
                    .headerMapping(ConnectivityModelFactory.newHeaderMapping(MAPPING))
                    .build();

    public static final JsonObject REPLY_TARGET_JSON = JsonObject
            .newBuilder()
            .set(ReplyTarget.JsonFields.ADDRESS, ADDRESS)
            .set(ReplyTarget.JsonFields.HEADER_MAPPING, MAPPING)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableReplyTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableReplyTarget.class, areImmutable(),
                provided(HeaderMapping.class, PayloadMapping.class).areAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = REPLY_TARGET.toJson();
        assertThat(actual).isEqualTo(REPLY_TARGET_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ReplyTarget actual = ImmutableReplyTarget.fromJson(REPLY_TARGET_JSON);
        assertThat(actual).isEqualTo(REPLY_TARGET);
    }
}
