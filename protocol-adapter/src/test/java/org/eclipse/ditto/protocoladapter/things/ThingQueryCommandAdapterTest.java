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
package org.eclipse.ditto.protocoladapter.things;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingQueryCommandAdapter}.
 */
public final class ThingQueryCommandAdapterTest implements ProtocolAdapterTest {

    private ThingQueryCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingQueryCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownThingQueryCommand());
    }

    @Test
    public void retrieveThingFromAdaptable() {
        final RetrieveThing expected = RetrieveThing.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThing retrieveThing =
                RetrieveThing.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveThing);

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(actual);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson();
        System.out.println(jsonObject.toString());

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final RetrieveThing expected = RetrieveThing.getBuilder(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2)
                .withSelectedFields(selectedFields)
                .build();

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withFields(selectedFields)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("thingId");
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThing retrieveThing =
                RetrieveThing.getBuilder(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE)
                        .withSelectedFields(selectedFields)
                        .build();
        final Adaptable actual = underTest.toAdaptable(retrieveThing);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclFromAdaptable() {
        final RetrieveAcl expected = RetrieveAcl.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAcl retrieveAcl =
                RetrieveAcl.of(TestConstants.THING_ID, TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAcl);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclEntryFromAdaptable() {
        final RetrieveAclEntry expected = RetrieveAclEntry
                .of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclEntryToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAclEntry retrieveAclEntry =
                RetrieveAclEntry.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAclEntry);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclEntryWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("READ");
        final RetrieveAclEntry expected =
                RetrieveAclEntry.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT, selectedFields,
                        TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAclEntryWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("READ");
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final RetrieveAclEntry retrieveAclEntry =
                RetrieveAclEntry.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT, selectedFields,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAclEntry);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesFromAdaptable() {
        final RetrieveAttributes expected =
                RetrieveAttributes.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttributes retrieveAttributes =
                RetrieveAttributes.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttributes);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveAttributes expected =
                RetrieveAttributes.of(TestConstants.THING_ID, selectedFields, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttributes retrieveAttributes =
                RetrieveAttributes.of(TestConstants.THING_ID, selectedFields,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttributes);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeFromAdaptable() {
        final RetrieveAttribute expected = RetrieveAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveAttributeToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveAttribute retrieveAttribute =
                RetrieveAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveAttribute);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveDefinitionFromAdaptable() {
        final RetrieveThingDefinition expected =
                RetrieveThingDefinition.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveDefinitionToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveThingDefinition retrieveDefinition =
                RetrieveThingDefinition.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveDefinition);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesFromAdaptable() {
        final RetrieveFeatures expected =
                RetrieveFeatures.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatures retrieveFeatures =
                RetrieveFeatures.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatures);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveFeatures expected =
                RetrieveFeatures.of(TestConstants.THING_ID, selectedFields, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatures retrieveFeatures =
                RetrieveFeatures.of(TestConstants.THING_ID, selectedFields, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatures);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureFromAdaptable() {
        final RetrieveFeature expected =
                RetrieveFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeature retrieveFeature =
                RetrieveFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeature);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionFromAdaptable() {
        final RetrieveFeatureDefinition expected =
                RetrieveFeatureDefinition.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureDefinition retrieveFeatureDefinition =
                RetrieveFeatureDefinition.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureDefinition);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesFromAdaptable() {
        final RetrieveFeatureProperties expected =
                RetrieveFeatureProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureProperties retrieveFeatureProperties =
                RetrieveFeatureProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperties);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final RetrieveFeatureProperties expected =
                RetrieveFeatureProperties.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, selectedFields,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertiesWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("foo");
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureProperties retrieveFeatureProperties = RetrieveFeatureProperties
                .of(TestConstants.THING_ID, TestConstants.FEATURE_ID, selectedFields,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperties);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyFromAdaptable() {
        final RetrieveFeatureProperty expected =
                RetrieveFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveFeaturePropertyToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrieveFeatureProperty retrieveFeatureProperty =
                RetrieveFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(retrieveFeatureProperty);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingsFromAdaptableWithSpecificNamespace() {
        retrieveThingsFromAdaptable("org.eclipse.ditto.example");
    }

    @Test
    public void retrieveThingsFromAdaptableWithWildcardNamespace() {
        retrieveThingsFromAdaptable(null);
    }

    private void retrieveThingsFromAdaptable(final String namespace) {
        final String namespaceOfThings = "org.eclipse.ditto.example";
        final ThingId id1 = ThingId.of(namespaceOfThings, "id1");
        final ThingId id2 = ThingId.of(namespaceOfThings, "id2");
        final RetrieveThings expected =
                RetrieveThings.getBuilder(
                        Arrays.asList(id1, id2))
                        .dittoHeaders(TestConstants.DITTO_HEADERS_V_2)
                        .namespace(namespace)
                        .build();
        final TopicPath topicPath = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"))
                .twin()
                .commands()
                .retrieve()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonFactory.newObject()
                                .setValue("thingIds", JsonFactory.newArray()
                                        .add("org.eclipse.ditto.example:id1")
                                        .add("org.eclipse.ditto.example:id2"))).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingsToAdaptable() {
        retrieveThingsToAdaptableWith("org.eclipse.ditto.example");
    }

    @Test
    public void retrieveThingsToAdaptableWithWildcardNamespace() {
        retrieveThingsToAdaptableWith(null);
    }

    private void retrieveThingsToAdaptableWith(final String namespace) {
        final TopicPath topicPath = TopicPath.fromNamespace(Optional.ofNullable(namespace).orElse("_"))
                .twin()
                .commands()
                .retrieve()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonFactory.newObject()
                                .setValue("thingIds", JsonFactory.newArray()
                                        .add("org.eclipse.ditto.example:id1")
                                        .add("org.eclipse.ditto.example:id2"))).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final String namespaceOfThings = "org.eclipse.ditto.example";
        final ThingId id1 = ThingId.of(namespaceOfThings, "id1");
        final ThingId id2 = ThingId.of(namespaceOfThings, "id2");

        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(Arrays.asList(id1, id2))
                        .dittoHeaders(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE)
                        .namespace(namespace)
                        .build();

        final Adaptable actual = underTest.toAdaptable(retrieveThings);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingQueryCommand implements ThingQueryCommand {

        @Override
        public String getType() {
            return "things.commands:retrievePolicyId";
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(JsonFields.TYPE, getType())
                    .set("thingId", getThingEntityId().toString())
                    .build();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_2;
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/policyId");
        }

        @Override
        public ThingId getThingEntityId() {
            return TestConstants.THING_ID;
        }

        @Override
        public ThingQueryCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

    }

}
