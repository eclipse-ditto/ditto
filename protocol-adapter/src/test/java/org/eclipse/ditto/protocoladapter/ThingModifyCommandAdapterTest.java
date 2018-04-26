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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifyCommandAdapter}.
 */
public final class ThingModifyCommandAdapterTest {

    private ThingModifyCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingModifyCommandAdapter.newInstance();
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownThingModifyCommand());
    }

    @Test
    public void createThingFromAdaptable() {
        final CreateThing expected = CreateThing.of(TestConstants.THING, null, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .create()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void createThingToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .create()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final CreateThing createThing = CreateThing.of(TestConstants.THING, null,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(createThing);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingFromAdaptable() {
        final ModifyThing expected =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, null, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyThingToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyThing modifyThing =
                ModifyThing.of(TestConstants.THING_ID, TestConstants.THING, null,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyThing);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteThingFromAdaptable() {
        final DeleteThing expected = DeleteThing.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteThingToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteThing deleteThing =
                DeleteThing.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteThing);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAclFromAdaptable() {
        final ModifyAcl expected =
                ModifyAcl.of(TestConstants.THING_ID, TestConstants.ACL, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void modifyAclToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final ModifyAcl modifyAcl =
                ModifyAcl.of(TestConstants.THING_ID, TestConstants.ACL, TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAcl);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void modifyAclEntryFromAdaptable() {
        final ModifyAclEntry expected =
                ModifyAclEntry.of(TestConstants.THING_ID, TestConstants.ACL_ENTRY, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void modifyAclEntryToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL_ENTRY.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final ModifyAclEntry modifyAclEntry =
                ModifyAclEntry.of(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                        TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAclEntry);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void deleteAclEntryFromAdaptable() {
        final DeleteAclEntry expected = DeleteAclEntry.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT,
                TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void deleteAclEntryToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final DeleteAclEntry deleteAclEntry = DeleteAclEntry.of(TestConstants.THING_ID,
                TestConstants.AUTHORIZATION_SUBJECT, TestConstants.HEADERS_V_1_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAclEntry);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributesFromAdaptable() {
        final ModifyAttributes expected =
                ModifyAttributes.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttributes modifyAttributes =
                ModifyAttributes.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAttributes);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributesFromAdaptable() {
        final DeleteAttributes expected =
                DeleteAttributes.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttributes deleteAttributes =
                DeleteAttributes.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttributes);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributeFromAdaptable() {
        final ModifyAttribute expected = ModifyAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyAttributeToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyAttribute modifyAttribute = ModifyAttribute.of(TestConstants.THING_ID,
                TestConstants.ATTRIBUTE_POINTER, TestConstants.ATTRIBUTE_VALUE,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyAttribute);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributeFromAdaptable() {
        final DeleteAttribute expected =
                DeleteAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteAttributeToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteAttribute deleteAttribute =
                DeleteAttribute.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteAttribute);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturesFromAdaptable() {
        final ModifyFeatures expected =
                ModifyFeatures.of(TestConstants.THING_ID, TestConstants.FEATURES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatures modifyFeatures =
                ModifyFeatures.of(TestConstants.THING_ID, TestConstants.FEATURES,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatures);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturesFromAdaptable() {
        final DeleteFeatures expected = DeleteFeatures.of(TestConstants.THING_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatures deleteFeatures =
                DeleteFeatures.of(TestConstants.THING_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatures);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureFromAdaptable() {
        final ModifyFeature expected =
                ModifyFeature.of(TestConstants.THING_ID, TestConstants.FEATURE, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeature modifyFeature =
                ModifyFeature.of(TestConstants.THING_ID, TestConstants.FEATURE,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeature);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureFromAdaptable() {
        final DeleteFeature expected =
                DeleteFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeature deleteFeature =
                DeleteFeature.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeature);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDefinitionFromAdaptable() {
        final ModifyFeatureDefinition expected = ModifyFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureDefinition modifyFeatureDefinition = ModifyFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureDefinition);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDefinitionFromAdaptable() {
        final DeleteFeatureDefinition expected = DeleteFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeatureDefinitionToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureDefinition deleteFeatureDefinition = DeleteFeatureDefinition.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureDefinition);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertiesFromAdaptable() {
        final ModifyFeatureProperties expected = ModifyFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureProperties modifyFeatureProperties = ModifyFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTIES, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureProperties);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertiesFromAdaptable() {
        final DeleteFeatureProperties expected = DeleteFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertiesToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureProperties deleteFeatureProperties = DeleteFeatureProperties.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureProperties);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertyFromAdaptable() {
        final ModifyFeatureProperty expected =
                ModifyFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void modifyFeaturePropertyToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .modify()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ModifyFeatureProperty modifyFeatureProperty = ModifyFeatureProperty.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(modifyFeatureProperty);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertyFromAdaptable() {
        final DeleteFeatureProperty expected =
                DeleteFeatureProperty.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingModifyCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void deleteFeaturePropertyToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .commands()
                .delete()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final DeleteFeatureProperty deleteFeatureProperty = DeleteFeatureProperty.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER,
                TestConstants.HEADERS_V_2_NO_CONTENT_TYPE);
        final Adaptable actual = underTest.toAdaptable(deleteFeatureProperty);

        assertThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingModifyCommand implements ThingModifyCommand {

        @Override
        public String getType() {
            return "things.commands:modifyPolicyId";
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
                    .set("thingId", getThingId())
                    .set("policyId", "foo")
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
        public String getThingId() {
            return TestConstants.THING_ID;
        }

        @Override
        public Category getCategory() {
            return Category.MODIFY;
        }

        @Override
        public ThingModifyCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Override
        public boolean changesAuthorization() {
            return false;
        }

    }

}
