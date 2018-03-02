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

import java.beans.Introspector;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.EventRegistry;
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
import org.eclipse.ditto.signals.events.things.ThingEventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DBObject;

import akka.actor.ExtendedActorSystem;
import akka.persistence.journal.EventAdapter;
import akka.persistence.journal.EventSeq;
import akka.persistence.journal.Tagged;

/**
 * EventAdapter for {@link Event}s persisted into akka-persistence event-journal. Converts Event to MongoDB
 * BSON objects and vice versa.
 */
public final class ThingMongoEventAdapter implements EventAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingMongoEventAdapter.class);

    private static final String THING_ACL_MODIFIED = "thingAclModified";
    private static final String THING_ACL_ENTRY_DELETED = "thingAclEntryDeleted";
    private static final String THING_ACL_ENTRY_MODIFIED = "thingAclEntryModified";
    private static final String THING_ATTRIBUTES_DELETED = "thingAttributesDeleted";
    private static final String THING_ATTRIBUTES_MODIFIED = "thingAttributesModified";
    private static final String THING_ATTRIBUTE_MODIFIED = "thingAttributeModified";
    private static final String THING_ATTRIBUTE_DELETED = "thingAttributeDeleted";
    private static final String ATTRIBUTE = "attribute";
    private static final String PROPERTY = "property";

    private static final Predicate<JsonField> IS_REVISION = field -> field.getDefinition()
            .map(definition -> Objects.equals(definition, Event.JsonFields.REVISION))
            .orElse(false);

    private static final JsonPointer POLICY_IN_THING_EVENT_PAYLOAD = ThingEvent.JsonFields.THING.getPointer()
            .append(JsonPointer.of(Policy.INLINED_FIELD_NAME));

    // JSON field containing the event's payload.
    private static final JsonFieldDefinition<JsonObject> PAYLOAD =
            JsonFactory.newJsonObjectFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Map<String, Function<JsonObject, JsonObject>> migrationMappings;
    private final ExtendedActorSystem system;
    private final EventRegistry<ThingEvent> eventRegistry;

    public ThingMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        this.system = system;
        eventRegistry = ThingEventRegistry.newInstance();

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
    public String manifest(final Object event) {
        if (event instanceof Event) {
            return ((Event) event).getType();
        } else {
            throw new IllegalArgumentException(
                    "Unable to create manifest for a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event) {
            final Event<?> theEvent = (Event) event;
            final JsonSchemaVersion schemaVersion = theEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject =
                    theEvent.toJson(schemaVersion, IS_REVISION.negate().and(FieldType.regularOrSpecial())) //
                            // remove the policy entries from thing event payload
                            .remove(POLICY_IN_THING_EVENT_PAYLOAD);
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            final Object bson = dittoBsonJson.parse(jsonObject);
            final Set<String> readSubjects = calculateReadSubjects(theEvent);
            return new Tagged(bson, readSubjects);
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    private Set<String> calculateReadSubjects(final Event<?> theEvent) {
        return theEvent.getDittoHeaders().getReadSubjects().stream()
                .map(rs -> "rs:" + rs)
                .collect(Collectors.toSet());
    }

    @Override
    public EventSeq fromJournal(final Object event, final String manifest) {
        if (event instanceof DBObject) {
            final DBObject dbObject = (DBObject) event;
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            return EventSeq.single(tryToCreateEventFrom(dittoBsonJson.serialize(dbObject)));
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromJournal a non-'DBObject' object! Was: " + event.getClass());
        }
    }

    @Nullable
    private Event tryToCreateEventFrom(final JsonValue json) {
        try {
            return createEventFrom(json);
        } catch (final JsonParseException | DittoRuntimeException e) {
            final String message = MessageFormat.format("Could not deserialize ThingEvent JSON: ''{0}''", json);
            if (system != null) {
                system.log().error(e, message);
            } else {
                LOGGER.error(message, e);
            }
            return null;
        }
    }

    private Event createEventFrom(final JsonValue json) {
        final JsonObject jsonObject = json.asObject()
                .setValue(Event.JsonFields.REVISION.getPointer(), Event.DEFAULT_REVISION);

        return eventRegistry.parse(migrateComplex(migratePayload(jsonObject)), DittoHeaders.empty());
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
