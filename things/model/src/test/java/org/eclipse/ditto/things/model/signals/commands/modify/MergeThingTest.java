/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MergeThing}.
 */
public final class MergeThingTest {

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();
    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, MergeThing.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(MergeThing.JsonFields.JSON_PATH, TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER.toString())
            .set(MergeThing.JsonFields.JSON_VALUE, JsonValue.of(TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE))
            .build();

    private static final MergeThing KNOWN_MERGE_THING = MergeThing.withAttribute(TestConstants.Thing.THING_ID,
            TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER, TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, DITTO_HEADERS);

    @Test
    public void assertImmutability() {
        assertInstancesOf(MergeThing.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MergeThing.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void mergeWithThing() {
        final MergeThing mergeThing =
                MergeThing.withThing(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isTrue();
        assertThat(mergeThing.getPath()).isEmpty();
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Thing.THING.toJson());
    }

    @Test
    public void mergeWithPolicyId() {
        final MergeThing mergeThing = MergeThing.withPolicyId(
                TestConstants.Thing.THING_ID, TestConstants.Thing.POLICY_ID, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isTrue();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.POLICY_ID.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(JsonValue.of(TestConstants.Thing.POLICY_ID));
    }

    @Test(expected = NullPointerException.class)
    public void mergeWithNullPolicyId() {
        MergeThing.withPolicyId(TestConstants.Thing.THING_ID, null, DITTO_HEADERS);
    }

    @Test
    public void mergeWithThingDefinition() {
        final MergeThing mergeThing =
                MergeThing.withThingDefinition(TestConstants.Thing.THING_ID, TestConstants.Thing.DEFINITION, DITTO_HEADERS);

        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.DEFINITION.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Thing.DEFINITION.toJson());
    }

    @Test
    public void mergeWithNullThingDefinition() {
        final MergeThing mergeThing =
                MergeThing.withThingDefinition(TestConstants.Thing.THING_ID, ThingsModelFactory.nullDefinition(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.DEFINITION.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void mergeWithAttributes() {
        final MergeThing mergeThing =
                MergeThing.withAttributes(TestConstants.Thing.THING_ID, TestConstants.Thing.ATTRIBUTES, DITTO_HEADERS);
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.ATTRIBUTES.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Thing.ATTRIBUTES.toJson());
    }

    @Test
    public void mergeWithNullAttributes() {
        final MergeThing mergeThing =
                MergeThing.withAttributes(TestConstants.Thing.THING_ID, AttributesModelFactory.nullAttributes(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.ATTRIBUTES.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullObject());
    }

    @Test
    public void mergeWithAttribute() {
        final MergeThing mergeThing =
                MergeThing.withAttribute(TestConstants.Thing.THING_ID, TestConstants.Pointer.VALID_JSON_POINTER, TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.ATTRIBUTES.getPointer().append(
                TestConstants.Pointer.VALID_JSON_POINTER));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE);
    }

    @Test
    public void mergeWithNullAttribute() {
        final MergeThing mergeThing =
                MergeThing.withAttribute(TestConstants.Thing.THING_ID, TestConstants.Pointer.VALID_JSON_POINTER, JsonFactory.nullLiteral(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.ATTRIBUTES.getPointer().append(
                TestConstants.Pointer.VALID_JSON_POINTER));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void mergeWithInvalidAttributePath() {
        MergeThing.withAttribute(
                TestConstants.Thing.THING_ID, TestConstants.Pointer.INVALID_JSON_POINTER, TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void mergeWithInvalidAttributeValue() {
        MergeThing.withAttribute(
                TestConstants.Thing.THING_ID, TestConstants.Pointer.VALID_JSON_POINTER, TestConstants.Thing.INVALID_ATTRIBUTE_VALUE, DITTO_HEADERS);
    }

    @Test
    public void mergeWithFeatures() {
        final MergeThing mergeThing = MergeThing.withFeatures(TestConstants.Thing.THING_ID, TestConstants.Feature.FEATURES, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.FEATURES.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FEATURES.toJson());
    }

    @Test
    public void mergeWithNullFeatures() {
        final MergeThing mergeThing =
                MergeThing.withFeatures(TestConstants.Thing.THING_ID, ThingsModelFactory.nullFeatures(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.FEATURES.getPointer());
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void mergeWithFeature() {
        final MergeThing mergeThing =
                MergeThing.withFeature(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId())));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR.toJson());
    }

    @Test
    public void mergeWithNullFeature() {
        final MergeThing mergeThing =
                MergeThing.withFeature(TestConstants.Thing.THING_ID, ThingsModelFactory.nullFeature(
                        TestConstants.Feature.FLUX_CAPACITOR_ID), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId())));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void mergeWithFeatureDefinition() {
        final MergeThing mergeThing =
                MergeThing.withFeatureDefinition(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_DEFINITION, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.DEFINITION.getPointer()));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION.toJson());
    }

    @Test
    public void mergeWithNullFeatureDefinition() {
        final MergeThing mergeThing =
                MergeThing.withFeatureDefinition(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        ThingsModelFactory.nullFeatureDefinition(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.DEFINITION.getPointer()));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void mergeWithFeatureProperties() {
        final MergeThing mergeThing =
                MergeThing.withFeatureProperties(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.PROPERTIES.getPointer()));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJson());
    }

    @Test
    public void mergeWithNullFeatureProperties() {
        final MergeThing mergeThing =
                MergeThing.withFeatureProperties(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        ThingsModelFactory.nullFeatureProperties(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.PROPERTIES.getPointer()));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void mergeWithFeatureProperty() {
        final MergeThing mergeThing = MergeThing.withFeatureProperty(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.PROPERTIES.getPointer())
                        .append(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE);
    }

    @Test
    public void mergeWithNullFeatureProperty() {
        final MergeThing mergeThing = MergeThing.withFeatureProperty(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER,
                JsonFactory.nullLiteral(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.PROPERTIES.getPointer())
                        .append(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void mergeWithInvalidFeaturePropertyPath() {
        MergeThing.withFeatureProperty(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Pointer.INVALID_JSON_POINTER,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE, DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void mergeWithInvalidFeaturePropertyValue() {
        MergeThing.withFeatureProperty(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Pointer.VALID_JSON_POINTER,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE_WITH_INVALID_POINTER, DITTO_HEADERS);
    }

    @Test
    public void mergeWithDesiredFeatureProperties() {
        final MergeThing mergeThing =
                MergeThing.withFeatureDesiredProperties(
                        TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer()));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJson());
    }

    @Test
    public void mergeWithNullDesiredFeatureProperties() {
        final MergeThing mergeThing =
                MergeThing.withFeatureDesiredProperties(
                        TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        ThingsModelFactory.nullFeatureProperties(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer()));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void mergeWithDesiredFeatureProperty() {
        final MergeThing mergeThing = MergeThing.withFeatureDesiredProperty(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE, DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer())
                        .append(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER));
        assertThat(mergeThing.getValue()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE);
    }

    @Test
    public void mergeWithNullDesiredFeatureProperty() {
        final MergeThing mergeThing = MergeThing.withFeatureDesiredProperty(TestConstants.Thing.THING_ID,
                TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER,
                JsonFactory.nullLiteral(), DITTO_HEADERS);

        assertThat(mergeThing.changesAuthorization()).isFalse();
        assertThat(mergeThing.getPath()).isEqualTo(
                Thing.JsonFields.FEATURES.getPointer()
                        .append(JsonPointer.of(TestConstants.Feature.FLUX_CAPACITOR.getId()))
                        .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer())
                        .append(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER));
        assertThat(mergeThing.getValue()).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void mergeWithInvalidDesiredFeaturePropertyPath() {
        MergeThing.withFeatureDesiredProperty(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Pointer.INVALID_JSON_POINTER,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE, DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void mergeWithInvalidDesiredFeaturePropertyValue() {
        MergeThing.withFeatureDesiredProperty(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Pointer.VALID_JSON_POINTER,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE_WITH_INVALID_POINTER, DITTO_HEADERS);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final MergeThing mergeThing = MergeThing.fromJson(KNOWN_JSON, DITTO_HEADERS);

        assertThat(mergeThing).isEqualTo(KNOWN_MERGE_THING);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = KNOWN_MERGE_THING.toJson();

        DittoJsonAssertions.assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getEntityReturnsExpected() {
        final MergeThing underTest = MergeThing.withAttribute(TestConstants.Thing.THING_ID, TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, DITTO_HEADERS);
        final Optional<JsonValue> entity = underTest.getEntity();

        Assertions.assertThat(entity).contains(TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE);
    }

    @Test
    public void mergeTooLargeThing() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        final JsonObject largeAttributes = JsonObject.newBuilder()
                .set("a", sb.toString())
                .build();
        final ThingId thingId = ThingId.of("foo", "bar");
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(largeAttributes)
                .build();

        assertThatThrownBy(() -> MergeThing.withThing(thingId, thing, DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
