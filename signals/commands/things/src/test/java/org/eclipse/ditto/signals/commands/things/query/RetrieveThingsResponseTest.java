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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveThingsResponse}.
 */
public class RetrieveThingsResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, RetrieveThingsResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(RetrieveThingsResponse.JSON_THINGS, JsonFactory.newArray().add(TestConstants.Thing.THING.toJson()))
            .set(RetrieveThingsResponse.JSON_NAMESPACE, "example.com")
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThingsResponse.class, areImmutable(), provided(JsonArray.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveThingsResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThings() {
        RetrieveThingsResponse.of((List<Thing>) null, FieldType.notHidden(), "some.namespace",
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonArray() {
        RetrieveThingsResponse.of((JsonArray) null, "some.namespace", TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveThingsResponse underTest = RetrieveThingsResponse
                .of(Collections.singletonList(TestConstants.Thing.THING), FieldType.notHidden(), "example.com",
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceWithNullNamespaces() {
        final RetrieveThingsResponse retrieveThingsResponse =
                RetrieveThingsResponse.of(Collections.singletonList(TestConstants.Thing.THING), FieldType.notHidden(),
                        null, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(retrieveThingsResponse.getNamespace()).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void createInstanceWithInvalidNamespacesThrowsException() {
        RetrieveThingsResponse.of(Collections.singletonList(TestConstants.Thing.THING), FieldType.notHidden(),
                "namespace.that.does.not.match.the.result", TestConstants.EMPTY_DITTO_HEADERS);
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
