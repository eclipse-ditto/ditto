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
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
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

    @Mock
    private TopicPath topicPath;
    private DittoHeaders dittoHeaders;

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
        Mockito.when(adaptable.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
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
    public void getPayloadValueAsJsonObjectOrThrowReturnsExpectedJsonObjectIfContainedInPayload() {
        final JsonObject jsonObject = JsonObject.newBuilder().set("foo", "bar").build();
        final Payload payload = ProtocolFactory.newPayloadBuilder().withValue(jsonObject).build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPayloadValueAsJsonObjectOrThrow()).isEqualTo(jsonObject);
    }

    @Test
    public void getPayloadValueAsJsonObjectOrThrowThrowsExceptionIfPayloadValueIsNoJsonObject() {
        final JsonValue jsonValue = JsonValue.of(false);
        final Payload payload = ProtocolFactory.newPayloadBuilder().withValue(jsonValue).build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getPayloadValueAsJsonObjectOrThrow)
                .withMessage("Payload value is not a JSON object but <%s>.", jsonValue)
                .withNoCause();
    }

    @Test
    public void getPayloadValueAsJsonObjectOrThrowThrowsExceptionPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getPayloadValueAsJsonObjectOrThrow)
                .withMessage("Payload does not contain a JSON object value because it has no value at all.")
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
    public void getAttributePointerOrThrowReturnsPointerIfMessagePathHasAppropriatePrefix() {
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
    public void getAttributePointerOrThrowThrowsExceptionIfMessagePathHasAnInappropriatePrefix() {
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
    public void getAttributesOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getAttributesOrThrow)
                .withMessage("Payload does not contain an Attributes as JSON object because it has no value at all.")
                .withNoCause();
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
    public void getFeaturesOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeaturesOrThrow)
                .withMessage("Payload does not contain a Features as JSON string object it has no value at all.")
                .withNoCause();
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

        assertThat(underTest.getFeatureIdOrThrow()).isEqualTo(featureId);
    }

    @Test
    public void getFeatureIdOrThrowReturnsFeatureIdIfContainedInPayload2() {
        final String featureId = "HX711";
        final JsonPointer path = JsonPointer.of("features/" + featureId + "/definition");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureIdOrThrow()).isEqualTo(featureId);
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
    public void getFeatureIdOrThrowThrowsExceptionIfPayloadHasPathWithTooLessSegments() {
        final JsonPointer path = JsonPointer.of("features");
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
    public void getFeatureReturnsExpectedFeatureIfContainedInPayload() {
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

        assertThat(underTest.getFeature()).contains(feature);
    }

    @Test
    public void getFeatureReturnsEmptyOptionalIfPayloadHasNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeature()).isEmpty();
    }

    @Test
    public void getFeatureOrThrowThrowsExceptionIfPayloadHasNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureOrThrow)
                .withMessage("Payload does not contain a Feature as JSON object it has no value at all.")
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
                .isThrownBy(underTest::getFeature)
                .withMessage("Payload value is not a Feature as JSON object but <%s>.", jsonValue)
                .withNoCause();
    }

    @Test
    public void getFeatureThrowsExceptionIfMessagePathProvidesNoFeatureId() {
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
                .isThrownBy(underTest::getFeature)
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

    @Test
    public void getThingDefinitionOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getThingDefinitionOrThrow)
                .withMessage("Payload does not contain a ThingDefinition as JSON string because it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getFeatureDefinitionReturnsExpectedFeatureDefinitionIfContainedInPayload() {
        final FeatureDefinition featureDefinition =
                ThingsModelFactory.newFeatureDefinition(JsonArray.of(JsonValue.of("example:test:definition")));
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(featureDefinition.toJson())
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureDefinition()).contains(featureDefinition);
    }

    @Test
    public void getFeatureDefinitionThrowsExceptionIfPayloadContainsValueThatIsNoJsonString() {
        final JsonValue value = JsonValue.of(true);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureDefinition)
                .withMessage("Payload value is not a FeatureDefinition as JSON array but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getFeatureDefinitionReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureDefinition()).isEmpty();
    }

    @Test
    public void getFeatureDefinitionOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureDefinitionOrThrow)
                .withMessage("Payload does not contain a FeatureDefinition as JSON array because it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getFeaturePropertiesReturnsExpectedFeaturePropertiesIfContainedInPayload() {
        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set("uint8_t HX711_DT[3]", "{A1, 11, 7}")
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(featureProperties)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureProperties()).contains(featureProperties);
    }

    @Test
    public void getFeaturePropertiesThrowsExceptionIfPayloadContainsValueThatIsNoJsonObject() {
        final JsonValue value = JsonValue.of(true);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureProperties)
                .withMessage("Payload value is not a FeatureProperties as JSON object but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getFeaturePropertiesReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeatureProperties()).isEmpty();
    }

    @Test
    public void getFeaturePropertiesOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeaturePropertiesOrThrow)
                .withMessage("Payload does not contain a FeatureProperties as JSON object it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getFeaturePropertyPointerOrThrowReturnsPointerIfMessagePathHasAppropriatePrefix() {
        final JsonPointer path = JsonPointer.of("features/HX711/properties/uint8_t HX711_DT[3]");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        final JsonPointer featurePropertyPointer = underTest.getFeaturePropertyPointerOrThrow();

        assertThat((CharSequence) featurePropertyPointer).isEqualTo(path.getSubPointer(3).get());
    }

    @Test
    public void getFeaturePropertyPointerOrThrowThrowsExceptionIfMessagePathHasAnInappropriatePrefix() {
        final JsonPointer path = JsonPointer.of("rfeatures/HX711/properties/uint8_t HX711_DT[3]");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeaturePropertyPointerOrThrow)
                .withMessage("Message path of payload does not start with <%s>.", "/features")
                .withNoCause();
    }

    @Test
    public void getFeatureDesiredPropertyPointerOrThrowThrowsExceptionIfMessagePathHasNoPropertiesSegment() {
        final JsonPointer path = JsonPointer.of("features/HX711/foo/uint8_t HX711_DT[3]");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureDesiredPropertyPointerOrThrow)
                .withMessage("Message path of payload at level <2> is not <desiredProperties> but <foo>.")
                .withNoCause();
    }

    @Test
    public void getFeaturePropertyPointerOrThrowReturnsEmptyJsonPointerIfMessagePathHasNoPropertySubPointer() {
        final JsonPointer path = JsonPointer.of("features/HX711/properties/");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat((CharSequence) underTest.getFeaturePropertyPointerOrThrow()).isEqualTo(JsonPointer.empty());
    }

    @Test
    public void getFeatureDesiredPropertyPointerOrThrowReturnsPointerIfMessagePathHasAppropriatePrefix() {
        final JsonPointer path = JsonPointer.of("features/HX711/desiredProperties/uint8_t HX711_DT[3]");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        final JsonPointer featurePropertyPointer = underTest.getFeatureDesiredPropertyPointerOrThrow();

        assertThat((CharSequence) featurePropertyPointer).isEqualTo(path.getSubPointer(3).get());
    }

    @Test
    public void getFeatureDesiredPropertyPointerOrThrowThrowsExceptionIfMessagePathHasAnInappropriatePrefix() {
        final JsonPointer path = JsonPointer.of("rfeatures/HX711/desiredProperties/uint8_t HX711_DT[3]");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureDesiredPropertyPointerOrThrow)
                .withMessage("Message path of payload does not start with <%s>.", "/features")
                .withNoCause();
    }

    @Test
    public void getFeatureDesiredPropertyPointerOrThrowThrowsExceptionIfMessagePathHasUnexpectedLevelCount() {
        final String levelTwoKey = "uint8_t HX711_DT[3]";
        final JsonPointer path = JsonPointer.of("features/HX711/" + levelTwoKey);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeatureDesiredPropertyPointerOrThrow)
                .withMessage("Message path of payload at level <2> is not <desiredProperties> but <%s>.", levelTwoKey)
                .withNoCause();
    }

    @Test
    public void getFeaturePropertyPointerOrThrowThrowsExceptionIfMessagePathHasNoPropertiesSegment() {
        final JsonPointer path = JsonPointer.of("features/HX711/foo/uint8_t HX711_DT[3]");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withPath(path)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeaturePropertyPointerOrThrow)
                .withMessage("Message path of payload at level <2> is not <properties> but <foo>.")
                .withNoCause();
    }

    @Test
    public void getFeaturePropertyValueReturnsExpectedIfContainedInPayload() {
        final JsonValue propertyValue = JsonValue.of("{A1, 11, 7}");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(propertyValue)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeaturePropertyValue()).contains(propertyValue);
    }

    @Test
    public void getFeaturePropertyValueReturnsEmptyOptionalIfPayloadHasNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getFeaturePropertyValue()).isEmpty();
    }

    @Test
    public void getFeaturePropertyValueOrThrowThrowsExceptionIfPayloadHasNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getFeaturePropertyValueOrThrow)
                .withMessage("Payload does not contain a feature property value because it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getNamespaceReturnsExpectedNamespaceIfNotPlaceholder() {
        final String namespace = "org.ditto";
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(PolicyId.inNamespaceWithRandomName(namespace))
                .commands()
                .retrieve()
                .build();
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getNamespace()).contains(namespace);
    }

    @Test
    public void getNamespaceReturnsEmptyOptionalIfPlaceholder() {
        final String namespace = TopicPath.ID_PLACEHOLDER;
        final TopicPath topicPath = TopicPath.fromNamespace(namespace).twin().commands().retrieve().build();
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getNamespace()).isEmpty();
    }

    @Test
    public void getPolicyIdReturnsPolicyIfContainedInPayload() {
        final PolicyId policyId = PolicyId.inNamespaceWithRandomName("org.ditto");
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(JsonValue.of(policyId.toString()))
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPolicyId()).contains(policyId);
    }

    @Test
    public void getPolicyIdThrowsExceptionIfPayloadContainsNoJsonStringValue() {
        final JsonValue jsonValue = JsonValue.of(true);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(jsonValue)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getPolicyId)
                .withMessage("Payload value is not a PolicyId as JSON string but <%s>.", jsonValue)
                .withNoCause();
    }

    @Test
    public void getPolicyIdReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPolicyId()).isEmpty();
    }

    @Test
    public void getPolicyIdOrThrowThrowsExceptionIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getPolicyIdOrThrow)
                .withMessage("Payload does not contain a PolicyId as JSON string because it has no value at all.")
                .withNoCause();
    }

    @Test
    public void getPolicyIdFromTopicPathReturnsExpectedPolicyId() {
        final PolicyId policyId = PolicyId.inNamespaceWithRandomName("org.eclipse.ditto");
        final TopicPath topicPath = ProtocolFactory.newTopicPathBuilder(policyId).commands().modify().build();
        Mockito.when(adaptable.getTopicPath()).thenReturn(topicPath);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat((CharSequence) underTest.getPolicyIdFromTopicPath()).isEqualTo(policyId);
    }

    @Test
    public void getPolicyReturnsExpectedPolicyIfContainedInPayload() {
        final Policy policy = Policy.newBuilder()
                .setId(PolicyId.inNamespaceWithRandomName("org.eclipse.ditto"))
                .build();
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(policy.toJson())
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPolicy()).contains(policy);
    }

    @Test
    public void getPolicyThrowsExceptionIfPayloadContainsValueThatIsNoJsonObject() {
        final JsonValue value = JsonValue.of(true);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getPolicy)
                .withMessage("Payload value is not a Policy as JSON object but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getPolicyReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPolicy()).isEmpty();
    }

    @Test
    public void getPolicyEntryReturnsExpectedPolicyEntryIfContainedInPayload() {
        final PolicyEntry policyEntry = TestConstants.Policies.POLICY_ENTRY;
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(policyEntry.toJson())
                .withPath(JsonPointer.of("entries/" + TestConstants.Policies.POLICY_ENTRY_LABEL))
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPolicyEntry()).contains(policyEntry);
    }

    @Test
    public void getPolicyEntryThrowsExceptionIfPayloadContainsValueThatIsNoJsonObject() {
        final JsonValue value = JsonValue.of(true);
        final Payload payload = ProtocolFactory.newPayloadBuilder()
                .withValue(value)
                .build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(underTest::getPolicyEntry)
                .withMessage("Payload value is not a PolicyEntry as JSON object but <%s>.", value)
                .withNoCause();
    }

    @Test
    public void getPolicyEntryReturnsEmptyOptionalIfPayloadContainsNoValue() {
        final Payload payload = ProtocolFactory.newPayloadBuilder().build();
        Mockito.when(adaptable.getPayload()).thenReturn(payload);
        final MappingContext underTest = MappingContext.of(adaptable);

        assertThat(underTest.getPolicyEntry()).isEmpty();
    }

}