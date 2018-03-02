/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThingResponse}.
 */
public final class SudoRetrieveThingResponseTest {

    private static final String THING_ID = "org.eclipse.ditto.test:myThing";

    private static final Thing THING = Thing.newBuilder().setId(THING_ID).build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(SudoCommandResponse.JsonFields.TYPE, SudoRetrieveThingResponse.TYPE)
            .set(SudoCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(SudoRetrieveThingResponse.JSON_THING, THING.toJson())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThingResponse.class,
                areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThingResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final JsonObject thingJson = THING.toJson(FieldType.notHidden());
        final SudoRetrieveThingResponse underTest =
                SudoRetrieveThingResponse.of(thingJson, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.notHidden());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrieveThingResponse underTest =
                SudoRetrieveThingResponse.fromJson(KNOWN_JSON, EMPTY_DITTO_HEADERS);

        Assertions.assertThat(underTest).isNotNull();
        Assertions.assertThat(underTest.getThing()).isEqualTo(THING);
    }

    /** */
    @Test
    public void checkSudoCommandResponseRegistryWorks() {
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final SudoCommandResponse sudoCommandResponse =
                SudoCommandResponseRegistry.newInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThingResponse).isEqualTo(sudoCommandResponse);
    }

}
