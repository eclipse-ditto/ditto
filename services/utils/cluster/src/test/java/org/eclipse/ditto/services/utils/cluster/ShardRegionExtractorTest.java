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
package org.eclipse.ditto.services.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ShardRegionExtractor}.
 */
public final class ShardRegionExtractorTest {

    private static final int NUMBER_OF_SHARDS = 10;
    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thingId");

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders dittoHeaders;
    private ShardRegionExtractor underTest;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("authSubject")))
                .correlationId(testName.getMethodName())
                .schemaVersion(JsonSchemaVersion.LATEST)
                .build();

        underTest = ShardRegionExtractor.of(NUMBER_OF_SHARDS, GlobalMappingStrategies.getInstance());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ShardRegionExtractor.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void deserializeThingCommand() {
        final Thing thing = Thing.newBuilder().setId(THING_ID).build();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeaders);
        final ShardedMessageEnvelope shardedMessageEnvelope = ShardedMessageEnvelope.of(THING_ID, createThing.getType(),
                createThing.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial()), createThing.getDittoHeaders());
        final JsonObject messageEnvelope = shardedMessageEnvelope.toJson();

        final Object actual = underTest.entityMessage(messageEnvelope);

        assertThat(actual).isEqualTo(createThing);
    }

    @Test
    public void deserializeThingCommandResponse() {
        final Thing thing = Thing.newBuilder().setId(THING_ID).build();
        final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, dittoHeaders);
        final ShardedMessageEnvelope shardedMessageEnvelope =
                ShardedMessageEnvelope.of(THING_ID, createThingResponse.getType(),
                        createThingResponse.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial()),
                        createThingResponse.getDittoHeaders());
        final JsonObject messageEnvelope = shardedMessageEnvelope.toJson();

        final Object actual = underTest.entityMessage(messageEnvelope);

        assertThat(actual).isEqualTo(createThingResponse);
    }

    @Test
    public void deserializeThingErrorResponse() {
        final ThingNotAccessibleException thingNotAccessibleException = ThingNotAccessibleException.newBuilder(THING_ID)
                .dittoHeaders(dittoHeaders)
                .build();
        final ThingErrorResponse errorResponse =
                ThingErrorResponse.of(THING_ID, thingNotAccessibleException, dittoHeaders);
        final ShardedMessageEnvelope shardedMessageEnvelope =
                ShardedMessageEnvelope.of(THING_ID, errorResponse.getType(),
                        errorResponse.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial()),
                        errorResponse.getDittoHeaders());
        final JsonObject messageEnvelope = shardedMessageEnvelope.toJson();

        final Object actual = underTest.entityMessage(messageEnvelope);

        assertThat(actual).isEqualTo(errorResponse);
    }

}
