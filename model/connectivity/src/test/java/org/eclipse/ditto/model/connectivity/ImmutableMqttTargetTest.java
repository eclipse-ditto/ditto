/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.connectivity.Topic.TWIN_EVENTS;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ImmutableMqttTargetTest {

    private static final String ADDRESS = "amqp/target1";
    private static final AuthorizationContext ctx = AuthorizationModelFactory.newAuthContext(
            AuthorizationModelFactory.newAuthSubject("eclipse"), AuthorizationModelFactory.newAuthSubject("ditto"));

    private static final Target TARGET =
            ConnectivityModelFactory.newMqttTarget(ADDRESS, ctx, 1, TWIN_EVENTS);
    private static final JsonObject TARGET_JSON = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder().add(TWIN_EVENTS.getName()).build())
            .set(Target.JsonFields.ADDRESS, ADDRESS)
            .set(MqttTarget.JsonFields.QOS, 1)
            .set(Source.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableMqttTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableMqttTarget.class, areImmutable(),
                provided(AuthorizationContext.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = TARGET.toJson();
        assertThat(actual).isEqualTo(TARGET_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Target actual = ImmutableMqttTarget.fromJson(TARGET_JSON);
        assertThat(actual).isEqualTo(TARGET);
    }

}
