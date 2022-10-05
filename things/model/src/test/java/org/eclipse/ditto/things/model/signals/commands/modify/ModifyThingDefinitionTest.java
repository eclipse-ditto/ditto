/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ModifyThingDefinition}.
 */
public final class ModifyThingDefinitionTest {

    private static final ThingDefinition KNOWN_DEFINITION = ThingsModelFactory.newDefinition("example" +
            ":test:definition");
    private static final ThingDefinition NULL_DEFINITION = ThingsModelFactory.nullDefinition();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyThingDefinition.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyThingDefinition.JSON_DEFINITION, JsonValue.of(KNOWN_DEFINITION.toString()))
            .build();

    private static final JsonObject KNOWN_JSON_WITH_NULL_DEFINITION = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyThingDefinition.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyThingDefinition.JSON_DEFINITION, null)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyThingDefinition.class, areImmutable(),
                provided(ThingId.class, ThingDefinition.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyThingDefinition.class)
                .withRedefinedSuperclass()
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyThingDefinition underTest =
                ModifyThingDefinition.of(TestConstants.Thing.THING_ID, KNOWN_DEFINITION, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonWithNullDefinitionReturnsExpected() {
        final ModifyThingDefinition underTest =
                ModifyThingDefinition.of(TestConstants.Thing.THING_ID, NULL_DEFINITION, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_NULL_DEFINITION);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyThingDefinition underTest =
                ModifyThingDefinition.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty());

        ThingCommandAssertions.assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getDefinition().toString())
                .isEqualTo(KNOWN_DEFINITION.toString());
    }

    @Test
    public void createInstanceFromValidJsonWithNullDefinition() {
        final ModifyThingDefinition underTest =
                ModifyThingDefinition.fromJson(KNOWN_JSON_WITH_NULL_DEFINITION.toString(), DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getDefinition())
                .isEqualTo(NULL_DEFINITION);
    }

}
