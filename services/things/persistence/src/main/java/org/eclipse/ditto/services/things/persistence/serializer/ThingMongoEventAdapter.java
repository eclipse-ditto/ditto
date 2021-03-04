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
package org.eclipse.ditto.services.things.persistence.serializer;

import java.beans.Introspector;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link Event}s persisted into akka-persistence event-journal. Converts Event to MongoDB
 * BSON objects and vice versa.
 */
public final class ThingMongoEventAdapter extends AbstractMongoEventAdapter<ThingEvent<?>> {

    private static final String THING_ACL_MODIFIED = "thingAclModified";
    private static final String THING_ACL_ENTRY_DELETED = "thingAclEntryDeleted";
    private static final String THING_ACL_ENTRY_MODIFIED = "thingAclEntryModified";
    private static final String THING_ATTRIBUTES_DELETED = "thingAttributesDeleted";
    private static final String THING_ATTRIBUTES_MODIFIED = "thingAttributesModified";
    private static final String THING_ATTRIBUTE_MODIFIED = "thingAttributeModified";
    private static final String THING_ATTRIBUTE_DELETED = "thingAttributeDeleted";
    private static final String ATTRIBUTE = "attribute";
    private static final String PROPERTY = "property";

    private static final JsonPointer POLICY_IN_THING_EVENT_PAYLOAD = ThingEvent.JsonFields.THING.getPointer()
            .append(JsonPointer.of(Policy.INLINED_FIELD_NAME));

    // JSON field containing the event's payload.
    private static final JsonFieldDefinition<JsonObject> PAYLOAD =
            JsonFactory.newJsonObjectFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Map<String, Function<JsonObject, JsonObject>> migrationMappings;

    public ThingMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance());

        migrationMappings = new HashMap<>();
        migrationMappings.put(FeatureModified.NAME,
                jsonObject -> migrateModifiedToCreated(jsonObject, FeatureCreated.TYPE));

        migrationMappings.put(FeaturesModified.NAME,
                jsonObject -> migrateModifiedToCreated(jsonObject, FeaturesCreated.TYPE));

        migrationMappings.put(THING_ACL_MODIFIED, ThingMongoEventAdapter::migrateId);
        migrationMappings.put(THING_ACL_ENTRY_DELETED, ThingMongoEventAdapter::migrateId);
        migrationMappings.put(THING_ACL_ENTRY_MODIFIED,
                jsonObject -> migrateModifiedToCreated(migrateId(jsonObject), AclEntryCreated.TYPE));

        migrationMappings.put(THING_ATTRIBUTE_DELETED,
                jsonObject -> renameJsonPointer(ATTRIBUTE, migrateId(jsonObject)));
        migrationMappings.put(THING_ATTRIBUTE_MODIFIED, jsonObject -> renameValue(ATTRIBUTE,
                renameJsonPointer(ATTRIBUTE, migrateModifiedToCreated(migrateId(jsonObject), AttributeCreated.TYPE))));

        migrationMappings.put(THING_ATTRIBUTES_DELETED, ThingMongoEventAdapter::migrateId);
        migrationMappings.put(THING_ATTRIBUTES_MODIFIED,
                jsonObject -> migrateModifiedToCreated(migrateId(jsonObject), AttributesCreated.TYPE));

        migrationMappings.put(FeaturePropertyDeleted.NAME, jsonObject -> renameJsonPointer(PROPERTY, jsonObject));
        migrationMappings.put(FeaturePropertyModified.NAME, jsonObject -> renameValue(PROPERTY,
                renameJsonPointer(PROPERTY, migrateModifiedToCreated(jsonObject, FeaturePropertyCreated.TYPE))));

        migrationMappings.put(FeaturePropertiesModified.NAME,
                jsonObject -> migrateModifiedToCreated(jsonObject, FeaturePropertiesCreated.TYPE));
    }

    @Override
    protected JsonObject performToJournalMigration(final JsonObject jsonObject) {
        return jsonObject
                .remove(POLICY_IN_THING_EVENT_PAYLOAD); // remove the policy entries from thing event payload
    }

    @Override
    protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
        return migrateComplex(migratePayload(jsonObject));
    }

    /**
     * A "payload" object was wrapping the events payload until the introduction of "cr-commands 1.0.0".
     * This field has to be used as fallback for already persisted events with "things-model" < 3.0.0.
     * Removing this workaround is possible if we are sure that no "old" events are ever loaded again!
     */
    private JsonObject migratePayload(final JsonObject jsonObject) {
        return jsonObject.getValue(PAYLOAD)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(obj -> jsonObject.remove(PAYLOAD.getPointer()).setAll(obj))
                .orElse(jsonObject);
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    private static JsonObject migrateId(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID)
                .map(name -> name.replaceFirst("thing", ""))
                .map(Introspector::decapitalize)
                .map(name -> ThingEvent.TYPE_PREFIX + name)
                .map(JsonValue::of)
                .map(type -> jsonObject.setValue(Event.JsonFields.TYPE.getPointer(), type))
                .orElse(jsonObject);
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    private JsonObject migrateComplex(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID)
                .map(migrationMappings::get)
                .map(migration -> migration.apply(jsonObject))
                .orElse(jsonObject);
    }

    /**
     * Migrates old modified events that have a {@code created} field equals {@code true} to new created events.
     *
     * @param jsonObject JSON object to migrate.
     * @param createdType the new type to use if the modified event has a field {@code created} that's value is {@code
     * true}.
     * @return migrated JSON object.
     */
    private static JsonObject migrateModifiedToCreated(final JsonObject jsonObject, final String createdType) {
        // migrates old feature modified events with created true to
        return jsonObject.getValue("created")
                .filter(JsonValue::isBoolean)
                .map(JsonValue::asBoolean)
                .filter(created -> created)
                .map(created -> createdType)
                .map(JsonValue::of)
                .map(type -> jsonObject.setValue(Event.JsonFields.TYPE.getPointer(), type))
                .orElse(jsonObject);
    }

    private static JsonObject renameField(final CharSequence oldFieldName, final CharSequence newFieldName,
            final JsonObject jsonObject) {

        return jsonObject.getValue(oldFieldName)
                .map(value -> jsonObject.setValue(newFieldName, value))
                .orElse(jsonObject);
    }

    private static JsonObject renameJsonPointer(final String fieldName, final JsonObject jsonObject) {
        return renameField(fieldName + "JsonPointer", fieldName, jsonObject);
    }

    private static JsonObject renameValue(final String fieldName, final JsonObject jsonObject) {
        return renameField(fieldName + "Value", "value", jsonObject);
    }

}
