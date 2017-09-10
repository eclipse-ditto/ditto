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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests the {@link SyncThing}.
 */
public final class SyncThingTest {

    private static final String THING_ID = ":testId";

    private static final String JSON_V1 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.ID, SyncThing.NAME)
            .set(SyncThing.JSON_THING_ID, THING_ID)
            .build().toString();

    private static final String JSON_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SyncThing.TYPE)
            .set(SyncThing.JSON_THING_ID, THING_ID)
            .build().toString();

    /** */
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SyncThing.class,
                MutabilityMatchers.areImmutable(),
                AllowedReason.provided(AuthorizationContext.class, JsonFieldSelector.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SyncThing.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonWithSchemaVersion1ReturnsExpected() {
        final SyncThing underTest = SyncThing.of(THING_ID, DittoHeaders.empty());
        final JsonValue jsonValue = underTest.toJson(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());

        assertThat(jsonValue.toString()).isEqualTo(JSON_V1);
    }

    /** */
    @Test
    public void toJsonWithSchemaVersion2ReturnsExpected() {
        final SyncThing underTest = SyncThing.of(THING_ID, DittoHeaders.empty());
        final JsonValue jsonValue = underTest.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

        assertThat(jsonValue.toString()).isEqualTo(JSON_V2);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final SyncThing underTest = SyncThing.fromJson(JSON_V1, DittoHeaders.empty());

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingId()).isEqualTo(THING_ID);
    }

}
