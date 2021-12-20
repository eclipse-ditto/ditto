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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

/**
 * Simple test which checks if the model classes JSON representation is as expected.
 */
public final class ThingsJsonTest {

    private static final int KNOWN_ANSWER_INT = 42;
    private static final int KNOWN_ILLUMNIATI_INT = 23;
    private static final AuthorizationSubject KNOWN_AUTH_SUBJECT =
            AuthorizationModelFactory.newAuthSubject("owner-0815");

    /**
     *
     */
    @Test
    public void ensureFeatureFreeFormJsonCorrectness() {
        final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                .set("someInt", KNOWN_ANSWER_INT)
                .set("someString", "foo")
                .set("someBool", false)
                .set("someObj", JsonFactory.newObject(toMap("aKey", "aValue")))
                .build();

        final String featureId = "tester-2000";
        final Feature feature = Feature.newBuilder()
                .properties(featureProperties)
                .desiredProperties(featureProperties)
                .withId(featureId)
                .build();

        final JsonObject expectedJsonObject = JsonFactory.newObjectBuilder()
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("someInt", KNOWN_ANSWER_INT)
                        .set("someString", "foo")
                        .set("someBool", false)
                        .set("someObj", JsonFactory.newObject("{\"aKey\": \"aValue\"}"))
                        .build())
                .set("desiredProperties", JsonFactory.newObjectBuilder()
                        .set("someInt", KNOWN_ANSWER_INT)
                        .set("someString", "foo")
                        .set("someBool", false)
                        .set("someObj", JsonFactory.newObject("{\"aKey\": \"aValue\"}"))
                        .build())
                .build();

        assertThat(feature.toJson()).isEqualToIgnoringFieldDefinitions(expectedJsonObject);
    }

    /**
     *
     */
    @Test
    public void ensureThingWithOneFeatureJsonCorrectness() {
        final Attributes thingAttributes = Attributes.newBuilder()
                .set("someIntAttribute", KNOWN_ILLUMNIATI_INT)
                .set("someStringAttribute", "someAttrValue")
                .set("someBoolAttribute", true)
                .build();

        final JsonObjectBuilder featurePropertyJsonObjectBuilder = JsonFactory.newObjectBuilder();
        featurePropertyJsonObjectBuilder.set("someObj", JsonFactory.newObject(toMap("aKey", "aValue")));

        final String featureId = "tester-2000";
        final ThingId thingId = ThingId.of("test.ns", "myThing");
        final PolicyId policyId = PolicyId.of(thingId);

        final String expectedJson =
                "{" + "\"thingId\":\"" + thingId + "\",\"policyId\":\"" + policyId + "\",\"attributes\":{"
                        + "\"someIntAttribute\":23," + "\"someStringAttribute\":\"someAttrValue\","
                        + "\"someBoolAttribute\":true"
                        + "}," + "\"features\":{\"" + featureId + "\":{\"properties\":{"
                        + "\"someObj\":{\"aKey\":\"aValue\"}"
                        + "}" + "}}" + "}";

        final Feature feature =
                ImmutableFeature.of(featureId, ImmutableFeatureProperties.of(featurePropertyJsonObjectBuilder.build()));
        final Features features = ImmutableFeatures.of(Collections.singletonList(feature));

        final Thing thing = ImmutableThing.of(thingId, policyId, null, thingAttributes, features, ThingLifecycle.ACTIVE,
                TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED, TestConstants.Thing.CREATED,
                TestConstants.Metadata.METADATA);

        assertThat(thing.toJsonString()).isEqualTo(expectedJson);
    }

    /**
     *
     */
    @Test
    public void createThingWithOneFeatureFromJsonWorksAsExpected() {
        final Attributes thingAttributes = Attributes.newBuilder()
                .set("someIntAttribute", KNOWN_ILLUMNIATI_INT)
                .set("someStringAttribute", "someAttrValue")
                .set("someBoolAttribute", true)
                .build();

        final JsonObjectBuilder featurePropertyJsonObjectBuilder = JsonFactory.newObjectBuilder();
        featurePropertyJsonObjectBuilder.set("someObj", JsonFactory.newObject(toMap("aKey", "aValue")));

        final ThingId thingId = ThingId.of("test.ns", "myThing");
        final PolicyId policyId = PolicyId.of(thingId);
        final String featureId = "tester-2000";
        final String thingJson =
                "{ " + "\"thingId\": \"" + thingId + "\"," + "\"policyId\": \"" + policyId + "\", \"attributes\": {" +
                        "\"someStringAttribute\": \"someAttrValue\","
                        + "\"someIntAttribute\": 23," + "\"someBoolAttribute\": true" + "}," + "\"features\": { \"" +
                        featureId
                        + "\": { " + "\"featureId\": \"" + featureId + "\", " + "\"properties\": {"
                        + "\"someObj\": { \"aKey\": \"aValue\" }" + "}" + "}" + "}" + "}";

        final Feature feature =
                ImmutableFeature.of(featureId, ImmutableFeatureProperties.of(featurePropertyJsonObjectBuilder.build()));
        final Features features = ImmutableFeatures.of(Collections.singletonList(feature));

        final Thing expected = Thing.newBuilder()
                .setAttributes(thingAttributes)
                .setFeatures(features)
                .setPolicyId(policyId)
                .setId(thingId)
                .build();
        final Thing actual = ThingsModelFactory.newThing(thingJson);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonWithOnlyLifecycleSelected() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.LIFECYCLE);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.LIFECYCLE.getPointer(), TestConstants.Thing.LIFECYCLE.name())
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithOnlyRevisionSelected() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.REVISION);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.REVISION.getPointer(), TestConstants.Thing.REVISION_NUMBER)
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithOnlyModifiedSelected() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.MODIFIED);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.MODIFIED.getPointer(), TestConstants.Thing.MODIFIED.toString())
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithOnlyIdSelected() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.ID);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.ID.getPointer(), TestConstants.Thing.THING_ID.toString())
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithOnlyNamespaceSelected() {
        final String namespace = "example.com";

        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.NAMESPACE);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.NAMESPACE.getPointer(), namespace)
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    /**
     *
     */
    @Test
    public void toJsonWithOnlyAttributesSelected() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.ATTRIBUTES);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.ATTRIBUTES.getPointer(), TestConstants.Thing.ATTRIBUTES)
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithOnlyFeaturesSelected() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.FEATURES);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.FEATURES.getPointer(),
                        TestConstants.Feature.FEATURES.toJson(FieldType.notHidden()))
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithOnlyIdAndRevisionSelected() {
        final JsonFieldSelector fieldSelector =
                JsonFactory.newFieldSelector(Thing.JsonFields.ID, Thing.JsonFields.REVISION);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.ID.getPointer(), TestConstants.Thing.THING_ID.toString())
                .set(Thing.JsonFields.REVISION.getPointer(), TestConstants.Thing.REVISION_NUMBER)
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(fieldSelector);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.ID, TestConstants.Thing.THING_ID.toString())
                .set(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID.toString())
                .set(Thing.JsonFields.DEFINITION, JsonValue.of(TestConstants.Thing.DEFINITION.toString()))
                .set(Thing.JsonFields.ATTRIBUTES, TestConstants.Thing.ATTRIBUTES)
                .set(Thing.JsonFields.FEATURES, TestConstants.Feature.FEATURES.toJson())
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson();

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void toJsonWithSpecialFieldTypePredicateReturnsExpected() {
        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.LIFECYCLE, TestConstants.Thing.LIFECYCLE.name())
                .set(Thing.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
                .set(Thing.JsonFields.NAMESPACE, "example.com")
                .set(Thing.JsonFields.MODIFIED, TestConstants.Thing.MODIFIED.toString())
                .set(Thing.JsonFields.CREATED, TestConstants.Thing.CREATED.toString())
                .build();

        final JsonObject actualJson = TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2, FieldType.SPECIAL);

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    @Test
    public void thingWithNullAttributesOnlyToJsonReturnsExpected() {
        final Thing thing = Thing.newBuilder()
                .setNullAttributes()
                .build();

        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.ATTRIBUTES);

        final JsonObject actualJson = thing.toJson(fieldSelector);

        final JsonObject expectedJson = JsonFactory.newObjectBuilder()
                .set(Thing.JsonFields.ATTRIBUTES.getPointer(), ThingsModelFactory.nullAttributes())
                .build();

        assertThat(actualJson).isEqualToIgnoringFieldDefinitions(expectedJson);
    }

    private static Map<JsonKey, JsonValue> toMap(final CharSequence keyName, final String value) {
        return Collections.singletonMap(JsonFactory.newKey(keyName), JsonFactory.newValue(value));
    }

}
