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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.api.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrieveThingsResponse}.
 */
public final class SudoRetrieveThingsResponseTest {

    private static final List<Thing> KNOWN_THINGS =
            Collections.singletonList(ThingsModelFactory.newThingBuilder().setId(TestConstants.Thing.THING_ID).build());

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingSudoQueryCommandResponse.JsonFields.TYPE, SudoRetrieveThingsResponse.TYPE)
            .set(ThingSudoQueryCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())

            .set(SudoRetrieveThingsResponse.JSON_THINGS_PLAIN_JSON,
                    KNOWN_THINGS.stream()
                            .map(thing -> thing.toJson(FieldType.notHidden()))
                            .collect(JsonCollectors.valuesToArray()).toString())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThingsResponse.class,
                areImmutable(),
                provided(Thing.class).isAlsoImmutable(),
                assumingFields("things").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                assumingFields("things").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveThingsResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullJsonArray() {
        SudoRetrieveThingsResponse.of(null, FieldType.notHidden(), EMPTY_DITTO_HEADERS);
    }

    @Test
    public void responseStatusCodeIsOk() {
        final SudoRetrieveThingsResponse underTest = SudoRetrieveThingsResponse.of(KNOWN_THINGS, FieldType.notHidden(),
                EMPTY_DITTO_HEADERS);

        assertThat(underTest.getHttpStatus()).isSameAs(HttpStatus.OK);
    }

    @Test
    public void toJsonReturnsExpectedJson() {
        final SudoRetrieveThingsResponse underTest = SudoRetrieveThingsResponse.of(KNOWN_THINGS, FieldType.notHidden(),
                EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromJson() {
        final SudoRetrieveThingsResponse response =
                SudoRetrieveThingsResponse.of(KNOWN_THINGS.stream().map(Thing::toJsonString).collect(
                        Collectors.joining(",", "[", "]")),
                        EMPTY_DITTO_HEADERS);

        final SudoRetrieveThingsResponse responseFromJson =
                SudoRetrieveThingsResponse.fromJson(response.toJson(), EMPTY_DITTO_HEADERS);

        assertThat(responseFromJson).isEqualTo(response);
    }

    @Test
    public void checkSudoCommandResponseTypeWorks() {
        final SudoRetrieveThingsResponse sudoRetrieveThingsResponse =
                SudoRetrieveThingsResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final CommandResponse commandResponse =
                GlobalCommandResponseRegistry.getInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThingsResponse).isEqualTo(commandResponse);
    }

}
