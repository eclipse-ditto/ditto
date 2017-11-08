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

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingEventAdapter}.
 */
public final class ThingEventAdapterTest {

    private ThingEventAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = ThingEventAdapter.newInstance();
    }

    @Test(expected = UnknownEventException.class)
    public void unknownEventToAdaptable() {
        underTest.toAdaptable(new UnknownThingEvent());
    }

    @Test
    public void thingCreatedFromAdaptable() {
        final ThingCreated expected =
                ThingCreated.of(TestConstants.THING, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingCreated thingCreated =
                ThingCreated.of(TestConstants.THING, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(thingCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingModifiedFromAdaptable() {
        final ThingModified expected =
                ThingModified.of(TestConstants.THING, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingModified thingModified =
                ThingModified.of(TestConstants.THING, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(thingModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingDeletedFromAdaptable() {
        final ThingDeleted expected =
                ThingDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingDeleted thingDeleted =
                ThingDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(thingDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclModifiedFromAdaptable() {
        final AclModified expected = AclModified.of(TestConstants.THING_ID, TestConstants.ACL, TestConstants.REVISION,
                TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/acl");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final AclModified aclModified =
                AclModified.of(TestConstants.THING_ID, TestConstants.ACL, TestConstants.REVISION,
                        TestConstants.DITTO_HEADERS_V_1);
        final Adaptable actual = underTest.toAdaptable(aclModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclEntryCreatedFromAdaptable() {
        final AclEntryCreated expected = AclEntryCreated.of(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclEntryCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL_ENTRY.toJson())
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final AclEntryCreated aclEntryCreated = AclEntryCreated.of(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_1);
        final Adaptable actual = underTest.toAdaptable(aclEntryCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclEntryModifiedFromAdaptable() {
        final AclEntryModified expected = AclEntryModified.of(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL_ENTRY.getPermissions().toJson())
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclEntryModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ACL_ENTRY.toJson())
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final AclEntryModified aclEntryModified = AclEntryModified.of(TestConstants.THING_ID, TestConstants.ACL_ENTRY,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_1);
        final Adaptable actual = underTest.toAdaptable(aclEntryModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclEntryDeletedFromAdaptable() {
        final AclEntryDeleted expected = AclEntryDeleted.of(TestConstants.THING_ID, TestConstants.AUTHORIZATION_SUBJECT,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_1);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void aclEntryDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/acl/" + TestConstants.AUTHORIZATION_SUBJECT.getId());

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        final AclEntryDeleted aclEntryDeleted = AclEntryDeleted.of(TestConstants.THING_ID,
                TestConstants.AUTHORIZATION_SUBJECT, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_1);
        final Adaptable actual = underTest.toAdaptable(aclEntryDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesCreatedFromAdaptable() {
        final AttributesCreated expected = AttributesCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributesCreated attributesCreated =
                AttributesCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(attributesCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesModifiedFromAdaptable() {
        final AttributesModified expected = AttributesModified.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributesModified attributesModified = AttributesModified.of(TestConstants.THING_ID,
                TestConstants.ATTRIBUTES, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(attributesModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesDeletedFromAdaptable() {
        final AttributesDeleted expected =
                AttributesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributesDeleted attributesDeleted =
                AttributesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(attributesDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeCreatedFromAdaptable() {
        final AttributeCreated expected = AttributeCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributeCreated attributeCreated =
                AttributeCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(attributeCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeModifiedFromAdaptable() {
        final AttributeModified expected = AttributeModified.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributeModified attributeModified =
                AttributeModified.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.ATTRIBUTE_VALUE,
                        TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(attributeModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeDeletedFromAdaptable() {
        final AttributeDeleted expected = AttributeDeleted.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributeDeleted attributeDeleted = AttributeDeleted.of(TestConstants.THING_ID,
                TestConstants.ATTRIBUTE_POINTER, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(attributeDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresCreatedFromAdaptable() {
        final FeaturesCreated expected = FeaturesCreated.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturesCreated featuresCreated = FeaturesCreated.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featuresCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresModifiedFromAdaptable() {
        final FeaturesModified expected = FeaturesModified.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturesModified featuresModified = FeaturesModified.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featuresModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresDeletedFromAdaptable() {
        final FeaturesDeleted expected =
                FeaturesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturesDeleted featuresDeleted =
                FeaturesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featuresDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureCreatedFromAdaptable() {
        final FeatureCreated expected = FeatureCreated.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureCreated featureCreated = FeatureCreated.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featureCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureModifiedFromAdaptable() {
        final FeatureModified expected = FeatureModified.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureModified featureModified = FeatureModified.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featureModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDeletedFromAdaptable() {
        final FeatureDeleted expected = FeatureDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDeleted featureDeleted = FeatureDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featureDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesCreatedFromAdaptable() {
        final FeaturePropertiesCreated expected =
                FeaturePropertiesCreated.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES,
                        TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertiesCreated featurePropertiesCreated =
                FeaturePropertiesCreated.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES,
                        TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featurePropertiesCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesModifiedFromAdaptable() {
        final FeaturePropertiesModified expected =
                FeaturePropertiesModified.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertiesModified featurePropertiesModified =
                FeaturePropertiesModified.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featurePropertiesModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesDeletedFromAdaptable() {
        final FeaturePropertiesDeleted expected = FeaturePropertiesDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertiesDeleted featurePropertiesDeleted = FeaturePropertiesDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featurePropertiesDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyCreatedFromAdaptable() {
        final FeaturePropertyCreated expected = FeaturePropertyCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyCreatedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertyCreated featurePropertyCreated = FeaturePropertyCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featurePropertyCreated);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyModifiedFromAdaptable() {
        final FeaturePropertyModified expected = FeaturePropertyModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertyModified featurePropertyModified = FeaturePropertyModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featurePropertyModified);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyDeletedFromAdaptable() {
        final FeaturePropertyDeleted expected =
                FeaturePropertyDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.REVISION,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyDeletedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .deleted()
                .build();
        final JsonPointer path = JsonPointer
                .of("/features/" + TestConstants.FEATURE_ID + "/properties" +
                        TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertyDeleted featurePropertyDeleted =
                FeaturePropertyDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.REVISION,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(featurePropertyDeleted);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void policyIdCreatedFromAdaptable() {
        final PolicyIdCreated expected = PolicyIdCreated.of(TestConstants.THING_ID, TestConstants.THING_ID,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .created()
                .build();
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_ID))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void policyIdModifiedFromAdaptable() {
        final PolicyIdModified expected = PolicyIdModified.of(TestConstants.THING_ID, TestConstants.THING_ID,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_ID))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void policyIdModifiedToAdaptable() {
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .events()
                .modified()
                .build();
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_ID))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final PolicyIdModified policyIdModified = PolicyIdModified.of(TestConstants.THING_ID, TestConstants.THING_ID,
                TestConstants.REVISION, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(policyIdModified);

        assertThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingEvent implements ThingEvent {

        @Override
        public String getThingId() {
            return TestConstants.THING_ID;
        }

        @Override
        public String getType() {
            return "things.events:aclDeleted";
        }

        @Override
        public long getRevision() {
            return TestConstants.REVISION;
        }

        @Override
        public Event setRevision(final long revision) {
            return this;
        }

        @Override
        public Optional<Instant> getTimestamp() {
            return Optional.empty();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_1;
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(Event.JsonFields.TYPE, getType())
                    .set(Event.JsonFields.REVISION, getRevision())
                    .set(JsonFields.THING_ID, getThingId())
                    .build();
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/acl");
        }

        @Override
        public ThingEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

    }

}
