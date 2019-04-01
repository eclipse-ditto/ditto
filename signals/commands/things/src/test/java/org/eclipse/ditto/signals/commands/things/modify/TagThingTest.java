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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link TagThing}.
 */
public class TagThingTest {

    static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();
    static final String THING_ID = "foo:bar";

    static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, TagThing.TYPE)
            .set(ThingModifyCommand.JsonFields.JSON_THING_ID, THING_ID)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(TagThing.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TagThing.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final TagThing underTest =
                TagThing.of(THING_ID, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final TagThing underTest =
                TagThing.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(THING_ID);
    }
}
