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
package org.eclipse.ditto.things.api.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThingResponse}.
 */
public final class SudoRetrieveThingResponseTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "myThing");

    private static final Thing THING = Thing.newBuilder().setId(THING_ID).build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingSudoQueryCommandResponse.JsonFields.TYPE, SudoRetrieveThingResponse.TYPE)
            .set(ThingSudoQueryCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(SudoRetrieveThingResponse.JSON_THING, THING.toJson())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThingResponse.class,
                areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThingResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject thingJson = THING.toJson(FieldType.notHidden());
        final SudoRetrieveThingResponse underTest = SudoRetrieveThingResponse.of(thingJson, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.notHidden());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrieveThingResponse underTest = SudoRetrieveThingResponse.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        Assertions.assertThat(underTest).isNotNull();
        Assertions.assertThat(underTest.getThing()).isEqualTo(THING);
    }

    @Test
    public void checkSudoCommandResponseRegistryWorks() {
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final CommandResponse commandResponse =
                GlobalCommandResponseRegistry.getInstance().parse(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThingResponse).isEqualTo(commandResponse);
    }

}
