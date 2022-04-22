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
package org.eclipse.ditto.policies.enforcement.validators;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.policies.enforcement.validators.CommandWithOptionalEntityValidator}.
 */
public final class CommandWithOptionalEntityValidatorTest {

    private static final String NULL_CHARACTER = "\u0000";

    @Test
    public void illegalNullCharacterIsInvalidInString() {
        final JsonValue jsonValue = JsonValue.of(NULL_CHARACTER);

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(() -> CommandWithOptionalEntityValidator.getInstance().apply(createTestCommand(jsonValue)));
    }

    @Test
    public void illegalNullCharacterIsInvalidInArray() {
        final JsonArray jsonArray = JsonArray.newBuilder().add(JsonValue.of(NULL_CHARACTER)).build();

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(() -> CommandWithOptionalEntityValidator.getInstance().apply(createTestCommand(jsonArray)));
    }

    @Test
    public void illegalNullCharacterIsInvalidInObject() {
        final JsonObject jsonObject = JsonObject.newBuilder().set("prop", JsonValue.of(NULL_CHARACTER)).build();

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(
                        () -> CommandWithOptionalEntityValidator.getInstance().apply(createTestCommand(jsonObject)));
    }

    private DittoHeadersSettable<?> createTestCommand(final JsonValue jsonValue) {
        final ThingId thingId = ThingId.of("org.eclipse.ditto.test:myThing");
        final String featureId = "myFeature";
        final JsonPointer propertyJsonPointer = JsonPointer.of("/bumlux");
        return ModifyFeatureProperty.of(thingId, featureId, propertyJsonPointer, jsonValue, DittoHeaders.empty());
    }

}
