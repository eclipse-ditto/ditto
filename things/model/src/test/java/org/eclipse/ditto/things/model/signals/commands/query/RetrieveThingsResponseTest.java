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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveThingsResponse}.
 */
public final class RetrieveThingsResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveThingsResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(RetrieveThingsResponse.JSON_THINGS_PLAIN_JSON, "[" + TestConstants.Thing.THING.toJsonString() + "]")
            .set(RetrieveThingsResponse.JSON_NAMESPACE, "example.com")
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThingsResponse.class,
                areImmutable(),
                provided(JsonArray.class).isAlsoImmutable(),
                assumingFields("things").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveThingsResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThings() {
        RetrieveThingsResponse.of(null,
                FieldType.notHidden(),
                "some.namespace",
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonArray() {
        RetrieveThingsResponse.of((JsonArray) null, "some.namespace", TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveThingsResponse underTest =
                RetrieveThingsResponse.of(Collections.singletonList(TestConstants.Thing.THING),
                        FieldType.notHidden(),
                        "example.com",
                        TestConstants.EMPTY_DITTO_HEADERS);

        final JsonObject actualJson = underTest.toJson();

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceWithNullNamespaces() {
        final RetrieveThingsResponse retrieveThingsResponse =
                RetrieveThingsResponse.of(Collections.singletonList(TestConstants.Thing.THING),
                        FieldType.notHidden(),
                        null,
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(retrieveThingsResponse.getNamespace()).isEmpty();
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveThingsResponse underTest =
                RetrieveThingsResponse.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThings()).hasSize(1);
        assertThat(underTest.getThings().get(0).toJson()).isEqualTo(TestConstants.Thing.THING.toJson());
        assertThat(underTest.getNamespace()).contains("example.com");
    }

}
