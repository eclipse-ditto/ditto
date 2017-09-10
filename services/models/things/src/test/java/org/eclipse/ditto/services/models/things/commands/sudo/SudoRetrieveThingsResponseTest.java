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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.things.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThingsResponse}.
 */
public final class SudoRetrieveThingsResponseTest {

    private static final List<Thing> KNOWN_THINGS =
            Collections.singletonList(ThingsModelFactory.newThingBuilder().setId(TestConstants.Thing.THING_ID).build());

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(SudoCommandResponse.JsonFields.TYPE, SudoRetrieveThingsResponse.TYPE)
            .set(SudoCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())

            .set(SudoRetrieveThingsResponse.JSON_THINGS,
                    KNOWN_THINGS.stream()
                            .map(thing -> thing.toJson(FieldType.notHidden()))
                            .collect(JsonCollectors.valuesToArray()))
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThingsResponse.class,
                areImmutable(),
                provided(Thing.class).isAlsoImmutable(),
                assumingFields("things").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThingsResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullJsonArray() {
        SudoRetrieveThingsResponse.of(null, FieldType.notHidden(), EMPTY_DITTO_HEADERS);
    }

    /** */
    @Test
    public void responseStatusCodeIsOk() {
        final SudoRetrieveThingsResponse underTest = SudoRetrieveThingsResponse.of(KNOWN_THINGS, FieldType.notHidden(),
                EMPTY_DITTO_HEADERS);

        assertThat(underTest.getStatusCode()).isSameAs(HttpStatusCode.OK);
    }

    /** */
    @Test
    public void toJsonReturnsExpectedJson() {
        final SudoRetrieveThingsResponse underTest = SudoRetrieveThingsResponse.of(KNOWN_THINGS, FieldType.notHidden(),
                EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromJson() {
        final SudoRetrieveThingsResponse response = SudoRetrieveThingsResponse.of(KNOWN_THINGS, FieldType.notHidden(),
                EMPTY_DITTO_HEADERS);

        final SudoRetrieveThingsResponse responseFromJson =
                SudoRetrieveThingsResponse.fromJson(response.toJson(), EMPTY_DITTO_HEADERS);

        assertThat(responseFromJson).isEqualTo(response);
    }

    /** */
    @Test
    public void checkSudoCommandResponseTypeWorks() {
        final SudoRetrieveThingsResponse sudoRetrieveThingsResponse =
                SudoRetrieveThingsResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final SudoCommandResponse sudoCommandResponse =
                SudoCommandResponseRegistry.newInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThingsResponse).isEqualTo(sudoCommandResponse);
    }

}
