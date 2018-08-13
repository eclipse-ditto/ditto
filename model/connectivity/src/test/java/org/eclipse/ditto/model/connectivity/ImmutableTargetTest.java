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

public class ImmutableTargetTest {

    private static final String ADDRESS = "amqp/target1";
    private static final String THINGS_TWIN_EVENTS = "_/_/things/twin/events";
    private static final AuthorizationContext ctx = AuthorizationModelFactory.newAuthContext(
            AuthorizationModelFactory.newAuthSubject("eclipse"), AuthorizationModelFactory.newAuthSubject("ditto"));

    private static final Target
            TARGET_WITH_AUTH_CONTEXT = ConnectivityModelFactory.newTarget(ADDRESS, ctx, TWIN_EVENTS);
    private static final JsonObject TARGET_JSON_WITH_EMPTY_AUTH_CONTEXT = JsonObject
            .newBuilder()
            .set(Target.JsonFields.TOPICS, JsonFactory.newArrayBuilder().add(TWIN_EVENTS.getName()).build())
            .set(Target.JsonFields.ADDRESS, ADDRESS)
            .build();

    private static final JsonObject TARGET_JSON_WITH_AUTH_CONTEXT = TARGET_JSON_WITH_EMPTY_AUTH_CONTEXT.toBuilder()
            .set(Source.JsonFields.AUTHORIZATION_CONTEXT, JsonFactory.newArrayBuilder().add("eclipse", "ditto").build())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableTarget.class, areImmutable(),
                provided(AuthorizationContext.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = TARGET_WITH_AUTH_CONTEXT.toJson();
        assertThat(actual).isEqualTo(TARGET_JSON_WITH_AUTH_CONTEXT);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final Target actual = ImmutableTarget.fromJson(TARGET_JSON_WITH_AUTH_CONTEXT);
        assertThat(actual).isEqualTo(TARGET_WITH_AUTH_CONTEXT);
    }

}
