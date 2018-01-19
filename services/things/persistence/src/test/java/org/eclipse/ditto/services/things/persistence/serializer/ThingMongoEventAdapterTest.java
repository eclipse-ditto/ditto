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
package org.eclipse.ditto.services.things.persistence.serializer;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
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
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Test;

/**
 * Tests for {@link ThingMongoEventAdapter}.
 */
public final class ThingMongoEventAdapterTest {

    private final ThingMongoEventAdapter underTest;

    public ThingMongoEventAdapterTest() {
        underTest = new ThingMongoEventAdapter(null);
    }

    @Test
    public void deserializeThingCreatedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", ThingCreated.NAME)
                .set("__schemaVersion", 1)
                .set("/payload/thingId", TestConstants.Thing.THING_ID)
                .set("/payload/thing", TestConstants.Thing.THING_V1.toJson(JsonSchemaVersion.V_1))
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(ThingCreated.NAME);
        assertThat(event.getType()).isEqualTo(ThingCreated.TYPE);
        assertThat(event).isInstanceOf(ThingCreated.class);
        final ThingCreated thingCreated = (ThingCreated) event;
        assertThat(thingCreated.getThing().toJsonString()).isEqualTo(TestConstants.Thing.THING_V1.toJsonString());
        assertThat(thingCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
    }

    @Test
    public void deserializeAttributeCreatedV1() {
        final String attributePointer = "test1";
        final JsonValue attributeValue = JsonValue.of(1234);

        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAttributeModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("attributeJsonPointer", attributePointer)
                        .set("attributeValue", attributeValue)
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AttributeCreated.NAME);
        assertThat(event.getType()).isEqualTo(AttributeCreated.TYPE);
        assertThat(event).isInstanceOf(AttributeCreated.class);
        final AttributeCreated attributeCreated = (AttributeCreated) event;
        assertThat(attributeCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(attributeCreated.getAttributePointer()).isEqualTo(JsonPointer.of(attributePointer));
        assertThat(attributeCreated.getAttributeValue()).isEqualTo(attributeValue);
    }

    @Test
    public void deserializeAttributeModifiedV1() {
        final String attributePointer = "test1";
        final JsonValue attributeValue = JsonValue.of(1234);

        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAttributeModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("attributeJsonPointer", attributePointer)
                        .set("attributeValue", attributeValue)
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AttributeModified.NAME);
        assertThat(event.getType()).isEqualTo(AttributeModified.TYPE);
        assertThat(event).isInstanceOf(AttributeModified.class);
        final AttributeModified attributeModified = (AttributeModified) event;
        assertThat(attributeModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(attributeModified.getAttributePointer()).isEqualTo(JsonPointer.of(attributePointer));
        assertThat(attributeModified.getAttributeValue()).isEqualTo(attributeValue);
    }

    @Test
    public void deserializeAttributeDeletedV1() {
        final String attributePointer = "test1";

        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAttributeDeleted")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("attributeJsonPointer", attributePointer)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AttributeDeleted.NAME);
        assertThat(event.getType()).isEqualTo(AttributeDeleted.TYPE);
        assertThat(event).isInstanceOf(AttributeDeleted.class);
        final AttributeDeleted attributeDeleted = (AttributeDeleted) event;
        assertThat(attributeDeleted.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(attributeDeleted.getAttributePointer()).isEqualTo(JsonPointer.of(attributePointer));
    }

    @Test
    public void deserializeAttributesDeletedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAttributesDeleted")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AttributesDeleted.NAME);
        assertThat(event.getType()).isEqualTo(AttributesDeleted.TYPE);
        assertThat(event).isInstanceOf(AttributesDeleted.class);
        final AttributesDeleted attributesDeleted = (AttributesDeleted) event;
        assertThat(attributesDeleted.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
    }

    @Test
    public void deserializeAttributesCreatedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAttributesModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("attributes", TestConstants.Thing.ATTRIBUTES.toJson(JsonSchemaVersion.V_1))
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AttributesCreated.NAME);
        assertThat(event.getType()).isEqualTo(AttributesCreated.TYPE);
        assertThat(event).isInstanceOf(AttributesCreated.class);
        final AttributesCreated attributesCreated = (AttributesCreated) event;
        assertThat(attributesCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(attributesCreated.getCreatedAttributes().toJsonString())
                .isEqualTo(TestConstants.Thing.ATTRIBUTES.toJsonString());
    }

    @Test
    public void deserializeAttributesModifiedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAttributesModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("attributes", TestConstants.Thing.ATTRIBUTES.toJson(JsonSchemaVersion.V_1))
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AttributesModified.NAME);
        assertThat(event.getType()).isEqualTo(AttributesModified.TYPE);
        assertThat(event).isInstanceOf(AttributesModified.class);
        final AttributesModified attributesModified = (AttributesModified) event;
        assertThat(attributesModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(attributesModified.getModifiedAttributes().toJsonString())
                .isEqualTo(TestConstants.Thing.ATTRIBUTES.toJsonString());
    }

    @Test
    public void deserializeAclModifiedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAclModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("acl", TestConstants.Thing.ACL.toJson(JsonSchemaVersion.V_1))
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AclModified.NAME);
        assertThat(event.getType()).isEqualTo(AclModified.TYPE);
        assertThat(event).isInstanceOf(AclModified.class);
        final AclModified aclModified = (AclModified) event;
        assertThat(aclModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(aclModified.getAccessControlList().toJsonString()).isEqualTo(TestConstants.Thing.ACL.toJsonString());
    }

    @Test
    public void deserializeAclEntryDeletedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAclEntryDeleted")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("authorizationSubject", TestConstants.Authorization.AUTH_SUBJECT_OLDMAN.getId())
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AclEntryDeleted.NAME);
        assertThat(event.getType()).isEqualTo(AclEntryDeleted.TYPE);
        assertThat(event).isInstanceOf(AclEntryDeleted.class);
        final AclEntryDeleted aclEntryDeleted = (AclEntryDeleted) event;
        assertThat(aclEntryDeleted.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(aclEntryDeleted.getAuthorizationSubject().getId())
                .isEqualTo(TestConstants.Authorization.AUTH_SUBJECT_OLDMAN.getId());
    }

    @Test
    public void deserializeAclEntryCreatedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAclEntryModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("aclEntry", TestConstants.Authorization.ACL_ENTRY_OLDMAN.toJson(JsonSchemaVersion.V_1))
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AclEntryCreated.NAME);
        assertThat(event.getType()).isEqualTo(AclEntryCreated.TYPE);
        assertThat(event).isInstanceOf(AclEntryCreated.class);
        final AclEntryCreated aclEntryCreated = (AclEntryCreated) event;
        assertThat(aclEntryCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(aclEntryCreated.getAclEntry().toJsonString())
                .isEqualTo(TestConstants.Authorization.ACL_ENTRY_OLDMAN.toJsonString());
    }

    @Test
    public void deserializeAclEntryModifiedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", "thingAclEntryModified")
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("aclEntry", TestConstants.Authorization.ACL_ENTRY_OLDMAN.toJson(JsonSchemaVersion.V_1))
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(AclEntryModified.NAME);
        assertThat(event.getType()).isEqualTo(AclEntryModified.TYPE);
        assertThat(event).isInstanceOf(AclEntryModified.class);
        final AclEntryModified aclEntryModified = (AclEntryModified) event;
        assertThat(aclEntryModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(aclEntryModified.getAclEntry().toJsonString())
                .isEqualTo(TestConstants.Authorization.ACL_ENTRY_OLDMAN.toJsonString());
    }

    @Test
    public void deserializeFeatureCreatedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", FeatureModified.NAME) // use modified with created true to simulate old created events
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("feature", TestConstants.Feature.FLUX_CAPACITOR.toJson(JsonSchemaVersion.V_1))
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeatureCreated.NAME);
        assertThat(event.getType()).isEqualTo(FeatureCreated.TYPE);
        assertThat(event).isInstanceOf(FeatureCreated.class);
        final FeatureCreated featureCreated = (FeatureCreated) event;
        assertThat(featureCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featureCreated.getFeature().toJsonString())
                .isEqualTo(TestConstants.Feature.FLUX_CAPACITOR.toJsonString());
    }

    @Test
    public void deserializeFeatureModifiedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", FeatureModified.NAME)
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("feature", TestConstants.Feature.FLUX_CAPACITOR.toJson(JsonSchemaVersion.V_1))
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeatureModified.NAME);
        assertThat(event.getType()).isEqualTo(FeatureModified.TYPE);
        assertThat(event).isInstanceOf(FeatureModified.class);
        final FeatureModified featureModified = (FeatureModified) event;
        assertThat(featureModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featureModified.getFeature().toJsonString())
                .isEqualTo(TestConstants.Feature.FLUX_CAPACITOR.toJsonString());
    }

    @Test
    public void deserializeFeaturesCreatedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", FeaturesModified.NAME) // use modified with created true to simulate old created events
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("features", TestConstants.Feature.FEATURES.toJson(JsonSchemaVersion.V_1))
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturesCreated.NAME);
        assertThat(event.getType()).isEqualTo(FeaturesCreated.TYPE);
        assertThat(event).isInstanceOf(FeaturesCreated.class);
        final FeaturesCreated featuresCreated = (FeaturesCreated) event;
        assertThat(featuresCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featuresCreated.getFeatures().toJsonString()).isEqualTo(
                TestConstants.Feature.FEATURES.toJsonString());
    }

    @Test
    public void deserializeFeaturesModifiedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", FeaturesModified.NAME)
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("features", TestConstants.Feature.FEATURES.toJson(JsonSchemaVersion.V_1))
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturesModified.NAME);
        assertThat(event.getType()).isEqualTo(FeaturesModified.TYPE);
        assertThat(event).isInstanceOf(FeaturesModified.class);
        final FeaturesModified featuresModified = (FeaturesModified) event;
        assertThat(featuresModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featuresModified.getFeatures().toJsonString())
                .isEqualTo(TestConstants.Feature.FEATURES.toJsonString());
    }

    @Test
    public void deserializeFeaturePropertyDeletedV1() {
        final JsonPointer propertyPointer = JsonPointer.of("test");

        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", FeaturePropertyDeleted.NAME)
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("propertyJsonPointer", propertyPointer.toString())
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturePropertyDeleted.NAME);
        assertThat(event.getType()).isEqualTo(FeaturePropertyDeleted.TYPE);
        assertThat(event).isInstanceOf(FeaturePropertyDeleted.class);
        final FeaturePropertyDeleted featurePropertyDeleted = (FeaturePropertyDeleted) event;
        assertThat(featurePropertyDeleted.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featurePropertyDeleted.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(featurePropertyDeleted.getPropertyPointer()).isEqualTo(propertyPointer);
    }

    @Test
    public void deserializeFeaturePropertyCreatedV1() {
        final JsonPointer propertyPointer = JsonPointer.of("test");
        final JsonValue propertyValue = JsonValue.of(1234);

        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event",
                        FeaturePropertyModified.NAME) // use modified with created true to simulate old created events
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("propertyJsonPointer", propertyPointer.toString())
                        .set("propertyValue", propertyValue)
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturePropertyCreated.NAME);
        assertThat(event.getType()).isEqualTo(FeaturePropertyCreated.TYPE);
        assertThat(event).isInstanceOf(FeaturePropertyCreated.class);
        final FeaturePropertyCreated featurePropertyCreated = (FeaturePropertyCreated) event;
        assertThat(featurePropertyCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featurePropertyCreated.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(featurePropertyCreated.getPropertyPointer()).isEqualTo(propertyPointer);
        assertThat(featurePropertyCreated.getPropertyValue()).isEqualTo(propertyValue);
    }

    @Test
    public void deserializeFeaturePropertyModifiedV1() {
        final JsonPointer propertyPointer = JsonPointer.of("test");
        final JsonValue propertyValue = JsonValue.of(1234);

        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event", FeaturePropertyModified.NAME)
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("propertyJsonPointer", propertyPointer.toString())
                        .set("propertyValue", propertyValue)
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturePropertyModified.NAME);
        assertThat(event.getType()).isEqualTo(FeaturePropertyModified.TYPE);
        assertThat(event).isInstanceOf(FeaturePropertyModified.class);
        final FeaturePropertyModified featurePropertyModified = (FeaturePropertyModified) event;
        assertThat(featurePropertyModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featurePropertyModified.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(featurePropertyModified.getPropertyPointer()).isEqualTo(propertyPointer);
        assertThat(featurePropertyModified.getPropertyValue()).isEqualTo(propertyValue);
    }

    @Test
    public void deserializeFeaturePropertiesCreatedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event",
                        FeaturePropertiesModified.NAME) // use modified with created true to simulate old created events
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("properties",
                                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJson(JsonSchemaVersion.V_1))
                        .set("created", true)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturePropertiesCreated.NAME);
        assertThat(event.getType()).isEqualTo(FeaturePropertiesCreated.TYPE);
        assertThat(event).isInstanceOf(FeaturePropertiesCreated.class);
        final FeaturePropertiesCreated featurePropertiesCreated = (FeaturePropertiesCreated) event;
        assertThat(featurePropertiesCreated.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featurePropertiesCreated.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(featurePropertiesCreated.getProperties().toJsonString())
                .isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJsonString());
    }

    @Test
    public void deserializeFeaturePropertiesModifiedV1() {
        final JsonObject eventJson = JsonFactory.newObjectBuilder()
                .set("event",
                        FeaturePropertiesModified.NAME) // use modified with created true to simulate old created events
                .set("__schemaVersion", 1)
                .set("payload", JsonFactory.newObjectBuilder()
                        .set("thingId", TestConstants.Thing.THING_ID)
                        .set("featureId", TestConstants.Feature.FLUX_CAPACITOR_ID)
                        .set("properties",
                                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJson(JsonSchemaVersion.V_1))
                        .set("created", false)
                        .build())
                .build();

        final Object object = toDbObject(eventJson);
        final Object actual = underTest.fromJournal(object, null).events().head();

        assertThat(actual).isInstanceOf(ThingEvent.class);
        final ThingEvent event = (ThingEvent) actual;
        assertThat(event.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(event.getName()).isEqualTo(FeaturePropertiesModified.NAME);
        assertThat(event.getType()).isEqualTo(FeaturePropertiesModified.TYPE);
        assertThat(event).isInstanceOf(FeaturePropertiesModified.class);
        final FeaturePropertiesModified featurePropertiesModified = (FeaturePropertiesModified) event;
        assertThat(featurePropertiesModified.getThingId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(featurePropertiesModified.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(featurePropertiesModified.getProperties().toJsonString())
                .isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.toJsonString());
    }

    private static Object toDbObject(final JsonObject jsonObject) {
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(jsonObject);
    }

}
