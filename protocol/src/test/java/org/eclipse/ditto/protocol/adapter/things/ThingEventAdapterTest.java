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
package org.eclipse.ditto.protocol.adapter.things;

import static org.eclipse.ditto.protocol.TopicPath.Channel.LIVE;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.EventsTopicPathBuilder;
import org.eclipse.ditto.protocol.LiveTwinTest;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownEventException;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeCreated;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesCreated;
import org.eclipse.ditto.things.model.signals.events.AttributesDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.FeatureCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionModified;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesModified;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyModified;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesModified;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.FeaturesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturesDeleted;
import org.eclipse.ditto.things.model.signals.events.FeaturesModified;
import org.eclipse.ditto.things.model.signals.events.PolicyIdModified;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingEventAdapter}.
 */
public final class ThingEventAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingEventAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingEventAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownEventException.class)
    public void unknownEventToAdaptable() {
        underTest.toAdaptable(new UnknownThingEvent(), channel);
    }

    @Test
    public void thingCreatedFromAdaptable() {
        final Instant now = Instant.now();
        final ThingCreated expected =
                ThingCreated.of(TestConstants.THING, TestConstants.REVISION, now,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(now)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.empty();

        final Instant now = Instant.now();
        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(now)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingCreated thingCreated =
                ThingCreated.of(TestConstants.THING, TestConstants.REVISION, now,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(thingCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingModifiedFromAdaptable() {
        final ThingModified expected =
                ThingModified.of(TestConstants.THING, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.regularOrSpecial()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.THING.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingModified thingModified =
                ThingModified.of(TestConstants.THING, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(thingModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingDeletedFromAdaptable() {
        final ThingDeleted expected =
                ThingDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void thingDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingDeleted thingDeleted =
                ThingDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(thingDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesCreatedFromAdaptable() {
        final AttributesCreated expected = AttributesCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributesCreated attributesCreated =
                AttributesCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(attributesCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesModifiedFromAdaptable() {
        final AttributesModified expected = AttributesModified.of(TestConstants.THING_ID, TestConstants.ATTRIBUTES,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributesModified attributesModified = AttributesModified.of(TestConstants.THING_ID,
                TestConstants.ATTRIBUTES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(attributesModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesDeletedFromAdaptable() {
        final AttributesDeleted expected =
                AttributesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributesDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes");

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributesDeleted attributesDeleted =
                AttributesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(attributesDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeCreatedFromAdaptable() {
        final AttributeCreated expected = AttributeCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributeCreated attributeCreated =
                AttributeCreated.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.ATTRIBUTE_VALUE, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(attributeCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeModifiedFromAdaptable() {
        final AttributeModified expected = AttributeModified.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.ATTRIBUTE_VALUE, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributeModified attributeModified =
                AttributeModified.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                        TestConstants.ATTRIBUTE_VALUE, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(attributeModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeDeletedFromAdaptable() {
        final AttributeDeleted expected = AttributeDeleted.of(TestConstants.THING_ID, TestConstants.ATTRIBUTE_POINTER,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void attributeDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/attributes" + TestConstants.ATTRIBUTE_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final AttributeDeleted attributeDeleted = AttributeDeleted.of(TestConstants.THING_ID,
                TestConstants.ATTRIBUTE_POINTER, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(attributeDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void definitionCreatedFromAdaptable() {
        final ThingDefinitionCreated expected = ThingDefinitionCreated.of(TestConstants.THING_ID,
                TestConstants.THING_DEFINITION, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_DEFINITION))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void definitionCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_DEFINITION))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingDefinitionCreated thingDefintionCreated =
                ThingDefinitionCreated.of(TestConstants.THING_ID, TestConstants.THING_DEFINITION,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(thingDefintionCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void definitionModifiedFromAdaptable() {
        final ThingDefinitionModified expected = ThingDefinitionModified.of(TestConstants.THING_ID,
                TestConstants.THING_DEFINITION, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_DEFINITION))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void definitionModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.THING_DEFINITION))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingDefinitionModified definitionModified = ThingDefinitionModified.of(TestConstants.THING_ID,
                TestConstants.THING_DEFINITION, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(definitionModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void definitionDeletedFromAdaptable() {
        final ThingDefinitionDeleted expected =
                ThingDefinitionDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void definitionDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final ThingDefinitionDeleted definitionDeleted =
                ThingDefinitionDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(definitionDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresCreatedFromAdaptable() {
        final FeaturesCreated expected = FeaturesCreated.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturesCreated featuresCreated = FeaturesCreated.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featuresCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresModifiedFromAdaptable() {
        final FeaturesModified expected = FeaturesModified.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURES.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturesModified featuresModified = FeaturesModified.of(TestConstants.THING_ID, TestConstants.FEATURES,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featuresModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresDeletedFromAdaptable() {
        final FeaturesDeleted expected =
                FeaturesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featuresDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features");

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturesDeleted featuresDeleted =
                FeaturesDeleted.of(TestConstants.THING_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featuresDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureCreatedFromAdaptable() {
        final FeatureCreated expected = FeatureCreated.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureCreated featureCreated = FeatureCreated.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureModifiedFromAdaptable() {
        final FeatureModified expected = FeatureModified.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE.toJson(FieldType.notHidden()))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureModified featureModified = FeatureModified.of(TestConstants.THING_ID, TestConstants.FEATURE,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDeletedFromAdaptable() {
        final FeatureDeleted expected = FeatureDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID);

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDeleted featureDeleted = FeatureDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesCreatedFromAdaptable() {
        final FeaturePropertiesCreated expected =
                FeaturePropertiesCreated.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertiesCreated featurePropertiesCreated =
                FeaturePropertiesCreated.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featurePropertiesCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesModifiedFromAdaptable() {
        final FeaturePropertiesModified expected =
                FeaturePropertiesModified.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertiesModified featurePropertiesModified =
                FeaturePropertiesModified.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTIES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featurePropertiesModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesDeletedFromAdaptable() {
        final FeaturePropertiesDeleted expected = FeaturePropertiesDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertiesDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/properties");

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertiesDeleted featurePropertiesDeleted = FeaturePropertiesDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featurePropertiesDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyCreatedFromAdaptable() {
        final FeaturePropertyCreated expected = FeaturePropertyCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertyCreated featurePropertyCreated = FeaturePropertyCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featurePropertyCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyModifiedFromAdaptable() {
        final FeaturePropertyModified expected = FeaturePropertyModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertyModified featurePropertyModified = FeaturePropertyModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.FEATURE_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featurePropertyModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyDeletedFromAdaptable() {
        final FeaturePropertyDeleted expected =
                FeaturePropertyDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featurePropertyDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/properties" + TestConstants.FEATURE_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeaturePropertyDeleted featurePropertyDeleted = FeaturePropertyDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_PROPERTY_POINTER, TestConstants.REVISION,
                TestConstants.TIMESTAMP, setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE),
                TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featurePropertyDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertiesCreatedFromAdaptable() {
        final FeatureDesiredPropertiesCreated expected =
                FeatureDesiredPropertiesCreated.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), null);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertiesCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDesiredPropertiesCreated featureDesiredPropertiesCreated =
                FeatureDesiredPropertiesCreated.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDesiredPropertiesCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertiesModifiedFromAdaptable() {
        final FeatureDesiredPropertiesModified expected =
                FeatureDesiredPropertiesModified.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertiesModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTIES_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDesiredPropertiesModified featureDesiredPropertiesModified =
                FeatureDesiredPropertiesModified.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTIES, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDesiredPropertiesModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertiesDeletedFromAdaptable() {
        final FeatureDesiredPropertiesDeleted expected = FeatureDesiredPropertiesDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertiesDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/desiredProperties");

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDesiredPropertiesDeleted featureDesiredPropertiesDeleted =
                FeatureDesiredPropertiesDeleted.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDesiredPropertiesDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertyCreatedFromAdaptable() {
        final FeatureDesiredPropertyCreated expected = FeatureDesiredPropertyCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                null);

        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertyCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDesiredPropertyCreated featureDesiredPropertyCreated =
                FeatureDesiredPropertyCreated.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDesiredPropertyCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertyModifiedFromAdaptable() {
        final FeatureDesiredPropertyModified expected = FeatureDesiredPropertyModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                TestConstants.REVISION, TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2),
                TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertyModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DESIRED_PROPERTY_VALUE)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDesiredPropertyModified featureDesiredPropertyModified =
                FeatureDesiredPropertyModified.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.FEATURE_DESIRED_PROPERTY_VALUE,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDesiredPropertyModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertyDeletedFromAdaptable() {
        final FeatureDesiredPropertyDeleted expected =
                FeatureDesiredPropertyDeleted.of(TestConstants.THING_ID, TestConstants.FEATURE_ID,
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER, TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDesiredPropertyDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of(
                "/features/" + TestConstants.FEATURE_ID + "/desiredProperties" +
                        TestConstants.FEATURE_DESIRED_PROPERTY_POINTER);

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDesiredPropertyDeleted featureDesiredPropertyDeleted =
                FeatureDesiredPropertyDeleted.of(TestConstants.THING_ID,
                        TestConstants.FEATURE_ID, TestConstants.FEATURE_DESIRED_PROPERTY_POINTER,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDesiredPropertyDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDefinitionCreatedFromAdaptable() {
        final FeatureDefinitionCreated expected = FeatureDefinitionCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.REVISION,
                TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDefinitionCreatedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPathCreated())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDefinitionCreated featureDefinitionCreated = FeatureDefinitionCreated.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.REVISION,
                TestConstants.TIMESTAMP, setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE),
                TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDefinitionCreated, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDefinitionModifiedFromAdaptable() {
        final FeatureDefinitionModified expected = FeatureDefinitionModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.REVISION,
                TestConstants.TIMESTAMP, setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDefinitionModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDefinitionModified featureDefinitionModified = FeatureDefinitionModified.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.FEATURE_DEFINITION, TestConstants.REVISION,
                TestConstants.TIMESTAMP, setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE),
                TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDefinitionModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDefinitionDeletedFromAdaptable() {
        final FeatureDefinitionDeleted expected = FeatureDefinitionDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withValue(TestConstants.FEATURE_DEFINITION_JSON)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void featureDefinitionDeletedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/features/" + TestConstants.FEATURE_ID + "/definition");

        final Adaptable expected = Adaptable.newBuilder(topicPathDeleted())
                .withPayload(Payload.newBuilder(path)
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final FeatureDefinitionDeleted featureDefinitionDeleted = FeatureDefinitionDeleted.of(TestConstants.THING_ID,
                TestConstants.FEATURE_ID, TestConstants.REVISION, TestConstants.TIMESTAMP,
                setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(featureDefinitionDeleted, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void policyIdModifiedFromAdaptable() {
        final PolicyIdModified expected =
                PolicyIdModified.of(TestConstants.THING_ID, TestConstants.Policies.POLICY_ID,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.DITTO_HEADERS_V_2), TestConstants.METADATA);

        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable adaptable = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.Policies.POLICY_ID))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .withMetadata(TestConstants.METADATA)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void policyIdModifiedToAdaptable() {
        final JsonPointer path = JsonPointer.of("/policyId");

        final Adaptable expected = Adaptable.newBuilder(topicPathModified())
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(TestConstants.Policies.POLICY_ID))
                        .withRevision(TestConstants.REVISION)
                        .withTimestamp(TestConstants.TIMESTAMP)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final PolicyIdModified policyIdModified =
                PolicyIdModified.of(TestConstants.THING_ID, TestConstants.Policies.POLICY_ID,
                        TestConstants.REVISION, TestConstants.TIMESTAMP,
                        setChannelHeader(TestConstants.HEADERS_V_2_NO_CONTENT_TYPE), TestConstants.METADATA);
        final Adaptable actual = underTest.toAdaptable(policyIdModified, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @AllValuesAreNonnullByDefault
    private static final class UnknownThingEvent implements ThingEvent<UnknownThingEvent> {

        @Override
        public String getType() {
            return "things.events:policyIdDeleted";
        }

        @Override
        public long getRevision() {
            return TestConstants.REVISION;
        }

        @Override
        public Optional<Instant> getTimestamp() {
            return Optional.empty();
        }

        @Override
        public Optional<Metadata> getMetadata() {
            return Optional.empty();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_2;
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(Event.JsonFields.TYPE, getType())
                    .set(EventsourcedEvent.JsonFields.REVISION, getRevision())
                    .set(JsonFields.THING_ID, getEntityId().toString())
                    .build();
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/policyId");
        }

        @Override
        public UnknownThingEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Override
        public Command.Category getCommandCategory() {
            return Command.Category.MODIFY;
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

        @Override
        public ThingId getEntityId() {
            return TestConstants.THING_ID;
        }
    }

    private DittoHeaders setChannelHeader(final DittoHeaders dittoHeaders) {
        if (channel == LIVE) {
            return dittoHeaders.toBuilder().channel(LIVE.getName()).build();
        } else {
            return dittoHeaders;
        }
    }

    private EventsTopicPathBuilder topicPathBuilder() {
        final TopicPathBuilder topicPathBuilder = TopicPath.newBuilder(TestConstants.THING_ID)
                .things();

        if (channel == LIVE) {
            topicPathBuilder.live();
        } else {
            topicPathBuilder.twin();
        }

        return topicPathBuilder.events();
    }

    private TopicPath topicPathCreated() {
        return topicPathBuilder().created().build();
    }

    private TopicPath topicPathModified() {
        return topicPathBuilder().modified().build();
    }

    private TopicPath topicPathDeleted() {
        return topicPathBuilder().deleted().build();
    }
}
