/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class ImmutableSourceTest {

    private static final AuthorizationContext ctx = AuthorizationModelFactory.newAuthContext(
            AuthorizationModelFactory.newAuthSubject("eclipse"), AuthorizationModelFactory.newAuthSubject("ditto"));

    private static final String AMQP_SOURCE1 = "amqp/source1";
    private static final Source SOURCE_WITH_AUTH_CONTEXT =
            ConnectivityModelFactory.newSourceWithSpecificConfig(2, 0, ctx,
                    Stream.of(new AbstractMap.SimpleEntry<>("qos", "0"))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                    AMQP_SOURCE1);

    private static final JsonObject SOURCE_JSON = JsonObject
            .newBuilder()
            .set(Source.JsonFields.ADDRESSES, JsonFactory.newArrayBuilder().add(AMQP_SOURCE1).build())
            .set(Source.JsonFields.CONSUMER_COUNT, 2)
            .build();

    private static final JsonObject SOURCE_JSON_WITH_AUTH_CONTEXT = SOURCE_JSON.toBuilder()
            .set(Source.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .set(Source.JsonFields.SPECIFIC_CONFIG, JsonFactory.newObject("{\"qos\":\"0\"}"))
            .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSource.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSource.class, areImmutable(),
                provided(AuthorizationContext.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = SOURCE_WITH_AUTH_CONTEXT.toJson();
        assertThat(actual).isEqualTo(SOURCE_JSON_WITH_AUTH_CONTEXT);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Source actual = ImmutableSource.fromJson(SOURCE_JSON_WITH_AUTH_CONTEXT, 0);
        assertThat(actual).isEqualTo(SOURCE_WITH_AUTH_CONTEXT);
    }

}
