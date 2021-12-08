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
package org.eclipse.ditto.protocol.mappingstrategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MappingContext}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MappingContextTest {

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private Adaptable adaptable;

    private DittoHeaders dittoHeaders;

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
        Mockito.when(adaptable.getDittoHeaders()).thenReturn(dittoHeaders);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MappingContext.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithNullAdaptableThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MappingContext.of(null))
                .withMessage("The adaptable must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithAdaptableReturnsNotNull() {
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest).isNotNull();
    }

    @Test
    public void getAdaptableReturnsExpectedAdaptable() {
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getAdaptable()).isEqualTo(adaptable);
    }

    @Test
    public void getThingIdReturnsExpectedThingId() {
        final ThingId thingId = ThingId.generateRandom();
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(thingId).twin().commands().modify().build();
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat((CharSequence) underTest.getThingId()).isEqualTo(thingId);
    }

    @Test
    public void getThingOrThrowReturnsThingIfContainedInPayload() {
        final Thing thing = Thing.newBuilder()
                .setId(ThingId.generateRandom())
                .setAttribute(JsonPointer.of("manufacturer"), JsonValue.of("ACME inc."))
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(thing.toJson())
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getThingOrThrow()).isEqualTo(thing);
    }

    @Test
    public void getThingOrThrowsThrowsExceptionIfPayloadContainsValueThatIsNoJsonObject() {
        final JsonValue value = JsonValue.of("ACME inc.");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getThingOrThrow)
                .withMessage("Payload value is not a Thing as JSON object but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getThingOrThrowThrowsExceptionPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getThingOrThrow)
                .withMessage("Payload does not contain a Thing as JSON object because it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getDittoHeadersReturnsExpectedDittoHeaders() {
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getDittoHeaders()).isEqualTo(dittoHeaders);
    }

    @Test
    public void getHttpStatusOrThrowReturnsExpectedHttpStatusIfContainedInPayload() {
        final HttpStatus httpStatus = HttpStatus.NO_CONTENT;
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withStatus(httpStatus)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        final HttpStatus actualHttpStatus = underTest.getHttpStatusOrThrow();

        assertThat(actualHttpStatus).isEqualTo(httpStatus);
    }

    @Test
    public void getHttpStatusOrThrowThrowsExceptionIfContainsNoHttpStatus() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getHttpStatusOrThrow)
                .withMessage("Payload does not contain a HTTP status.")
                .withNoCause();
    }

    @Test
    public void getAttributePointerOrThrowReturnsPointerIfItHasAppropriatePrefix() {
        final JsonPointer path = JsonPointer.of("attributes/manufacturer");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        final JsonPointer attributePointer = underTest.getAttributePointerOrThrow();

        assertThat((CharSequence) attributePointer).isEqualTo(path.nextLevel());
    }

    @Test
    public void getAttributePointerOrThrowThrowsExceptionIfItHasAnInappropriatePrefix() {
        final JsonPointer path = JsonPointer.of("ratattributes/manufacturer");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getAttributePointerOrThrow)
                .withMessage("Message path of payload does not start with <%s>.", "/attributes")
                .withNoCause();
    }

    @Test
    public void getAttributeValueReturnsExpectedValueIfContainedInPayload() {
        final JsonValue attributeValue = JsonValue.of("ACME inc.");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(attributeValue)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getAttributeValue()).contains(attributeValue);
    }

    @Test
    public void getAttributeValueReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getAttributeValue()).isEmpty();
    }

    @Test
    public void getAttributeValueOrThrowReturnsExpectedValueIfContainedInPayload() {
        final JsonValue attributeValue = JsonValue.of("ACME inc.");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(attributeValue)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        final JsonValue actualAttributeValue = underTest.getAttributeValueOrThrow();

        assertThat(actualAttributeValue).isEqualTo(attributeValue);
    }

    @Test
    public void getAttributeValueOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getAttributeValueOrThrow)
                .withMessage("Payload does not contain an attribute value.")
                .withNoCause();
    }

    @Test
    public void getAttributesReturnsExpectedIfContainedInPayload() {
        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set("manufacturer", "ACME inc.")
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(attributes)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getAttributes()).contains(attributes);
    }

    @Test
    public void getAttributesThrowsExceptionIfPayloadContainsValueThatIsNoJsonObject() {
        final JsonValue value = JsonValue.of("ACME inc.");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getAttributes)
                .withMessage("Payload value is not an Attributes as JSON object but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getAttributesReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getAttributes()).isEmpty();
    }

    @Test
    public void getFeaturesReturnsExpectedIfContainedInPayload() {
        final Features features = Features.newBuilder()
                .set(Feature.newBuilder()
                        .properties(FeatureProperties.newBuilder()
                                .set("uint8_t HX711_DT[3]", "{A1, 11, 7}")
                                .build())
                        .withId("HX711")
                        .build())
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(features.toJson())
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatures()).contains(features);
    }

    @Test
    public void getFeaturesThrowsExceptionIfPayloadContainsValueThatIsNoJsonObject() {
        final JsonValue value = JsonValue.of("ACME inc.");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatures)
                .withMessage("Payload value is not a Features as JSON object but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getFeaturesReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatures()).isEmpty();
    }

    @Test
    public void getFeatureIdOrThrowReturnsFeatureIdIfContainedInPayload() {
        final String featureId = "HX711";
        final JsonPointer featurePath = JsonPointer.of("features/" + featureId);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(featurePath)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureIdOrThrow()).isEqualTo(featureId.toString());
    }

    @Test
    public void getFeatureIdOrThrowThrowsExceptionIfPayloadHasPathWithUnexpectedPrefix() {
        final JsonPointer path = JsonPointer.of("rafeatures/HX711");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureIdOrThrow)
                .withMessage("Message path of payload does not start with <%s>.", "/features")
                .withNoCause();
    }

    @Test
    public void getFeatureIdOrThrowThrowsExceptionIfPayloadHasPathWithTooManySegments() {
        final JsonPointer path = JsonPointer.of("features/unexpected/HX711");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureIdOrThrow)
                .withMessage("Message path of payload does not contain a feature ID.")
                .withNoCause();
    }

    @Test
    public void getFeatureOrThrowReturnsFeatureIfContainedInPayload() {
        final Feature feature = Feature.newBuilder()
                .properties(FeatureProperties.newBuilder()
                        .set("uint8_t HX711_DT[3]", "{A1, 11, 7}")
                        .build())
                .withId("HX711")
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(feature.toJson())
                .withPath(JsonPointer.of("features/" + feature.getId()))
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureOrThrow()).isEqualTo(feature);
    }

    @Test
    public void getFeatureOrThrowThrowsExceptionIfPayloadHasNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureOrThrow)
                .withMessage("Payload does not contain a Feature as JSON object because it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getFeatureOrThrowThrowsExceptionIfPayloadContainsNoFeatureJsonObject() {
        final String featureId = "HX711";
        final JsonValue jsonValue = JsonValue.of(false);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(jsonValue)
                .withPath(JsonPointer.of("features/" + featureId))
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureOrThrow)
                .withMessage("Payload value is not a Feature as JSON object but <%s>.", jsonValue)
                .withNoCause();
    }

    @Test
    public void getFeatureOrThrowThrowsExceptionIfMessagePathProvidesNoFeatureId() {
        final Feature feature = Feature.newBuilder()
                .properties(FeatureProperties.newBuilder()
                        .set("uint8_t HX711_DT[3]", "{A1, 11, 7}")
                        .build())
                .withId("HX711")
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(feature.toJson())
                .withPath(JsonPointer.of("foo/" + feature.getId()))
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureOrThrow)
                .withMessageStartingWith("Message path of payload does not start with")
                .withNoCause();
    }

    @Test
    public void getThingDefinitionReturnsExpectedThingDefinitionIfContainedInPayload() {
        final ThingDefinition thingDefinition = ThingsModelFactory.newDefinition("example:test:definition");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(thingDefinition.toJson())
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getThingDefinition()).contains(thingDefinition);
    }

    @Test
    public void getThingDefinitionThrowsExceptionIfPayloadContainsValueThatIsNoJsonString() {
        final JsonArray value = JsonArray.of("foo", "bar", "baz");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getThingDefinition)
                .withMessage("Payload value is not a ThingDefinition as JSON string but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getThingDefinitionReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getThingDefinition()).isEmpty();
    }

}