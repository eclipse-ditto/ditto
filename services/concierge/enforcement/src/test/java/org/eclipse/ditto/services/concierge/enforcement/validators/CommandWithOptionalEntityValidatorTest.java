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
package org.eclipse.ditto.services.concierge.enforcement.validators;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.concierge.enforcement.validators.CommandWithOptionalEntityValidator}.
 */
public final class CommandWithOptionalEntityValidatorTest {

    private static final String NULL_CHARACTER = "\u0000";

    @Test
    public void illegalNullCharacterIsInvalidInString() {
        final JsonValue jsonValue = JsonValue.of(NULL_CHARACTER);

        final WithDittoHeaders withDittoHeaders = createTestCommand(jsonValue);

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(() -> CommandWithOptionalEntityValidator.getInstance().apply(withDittoHeaders));
    }

    @Test
    public void illegalNullCharacterIsInvalidInArray() {
        final JsonArray jsonArray = JsonArray.newBuilder().add(JsonValue.of(NULL_CHARACTER)).build();

        final WithDittoHeaders withDittoHeaders = createTestCommand(jsonArray);

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(() -> CommandWithOptionalEntityValidator.getInstance().apply(withDittoHeaders));
    }

    @Test
    public void illegalNullCharacterIsInvalidInObject() {
        final JsonObject jsonObject = JsonObject.newBuilder().set("prop", JsonValue.of(NULL_CHARACTER)).build();

        final WithDittoHeaders withDittoHeaders = createTestCommand(jsonObject);

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(() -> CommandWithOptionalEntityValidator.getInstance().apply(withDittoHeaders));
    }

    private WithDittoHeaders createTestCommand(final JsonValue jsonValue) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test:myThing");
        final String featureId = "myFeature";
        final JsonPointer propertyJsonPointer = JsonPointer.of("/bumlux");
        return ModifyFeatureProperty.of(thingId, featureId, propertyJsonPointer, jsonValue, DittoHeaders.empty());
    }

}
