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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyThingDefinitionResponse}.
 */
public final class ModifyThingDefinitionResponseTest {

    private static final ThingDefinition KNOWN_DEFINITION = ThingsModelFactory.newDefinition(
            "example:test:definition");

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyThingDefinitionResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.CREATED.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyThingDefinitionResponse.JSON_DEFINITION, KNOWN_DEFINITION.toString())
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyThingDefinitionResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyThingDefinitionResponse.class,
                areImmutable(),
                provided(ThingId.class, ThingDefinition.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyThingDefinitionResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyThingDefinitionResponse underTestCreated =
                ModifyThingDefinitionResponse.created(TestConstants.Thing.THING_ID,
                        KNOWN_DEFINITION,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyThingDefinitionResponse underTestUpdated =
                ModifyThingDefinitionResponse.modified(TestConstants.Thing.THING_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyThingDefinitionResponse underTestCreated =
                ModifyThingDefinitionResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        ThingCommandAssertions.assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getDefinition()).hasValue(KNOWN_DEFINITION);

        final ModifyThingDefinitionResponse underTestUpdated =
                ModifyThingDefinitionResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        ThingCommandAssertions.assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getDefinition()).isEmpty();
    }

}
