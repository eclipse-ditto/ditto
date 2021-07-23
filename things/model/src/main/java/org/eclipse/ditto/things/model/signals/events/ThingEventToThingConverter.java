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
package org.eclipse.ditto.things.model.signals.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Helpers and utils for converting {@link ThingEvent}s to {@link org.eclipse.ditto.things.model.Thing}s.
 */
public final class ThingEventToThingConverter {

    private static final Map<Class<?>, BiFunction<ThingEvent<?>, ThingBuilder.FromScratch, Thing>>
            EVENT_TO_THING_MAPPERS = createEventToThingMappers();

    private ThingEventToThingConverter() {
        throw new AssertionError();
    }

    /**
     * Creates a Thing from the passed ThingEvent.
     *
     * @param thingEvent the ThingEvent to extract the correlating Thing from
     * @return the Thing represented by the passed in ThingEvent
     */
    public static Optional<Thing> thingEventToThing(final ThingEvent<?> thingEvent) {

        return Optional.ofNullable(EVENT_TO_THING_MAPPERS.get(thingEvent.getClass()))
                .map(eventToThingMapper -> {
                    final ThingBuilder.FromScratch thingBuilder = Thing.newBuilder()
                            .setId(thingEvent.getEntityId())
                            .setRevision(thingEvent.getRevision())
                            .setModified(thingEvent.getTimestamp().orElse(null));
                    return eventToThingMapper.apply(thingEvent, thingBuilder);
                });
    }

    /**
     * Merge any thing information in a signal event together with extra fields from signal enrichment.
     * Thing events contain thing information. All other signals do not contain thing information.
     * Extra fields contain thing information if it is not empty.
     * If thing information exists in any of the 2 sources, then merge the information from both sources
     * to create a thing, with priority given to the thing information extracted by the {@code signal}.
     *
     * @param signal the signal.
     * @param extraFields selected extra fields to enrich the signal with.
     * @param extra value of the extra fields.
     * @return the merged thing if thing information exists in any of the 2 sources, or an empty optional otherwise.
     */
    public static Optional<Thing> mergeThingWithExtraFields(final Signal<?> signal,
            @Nullable final JsonFieldSelector extraFields,
            final JsonObject extra) {

        final Thing thing;
        final Optional<Thing> thingFromSignal;
        if (signal instanceof ThingEvent) {
            thingFromSignal = thingEventToThing((ThingEvent<?>) signal);
        } else {
            thingFromSignal = Optional.empty();
        }
        final boolean hasExtra = extraFields != null && !extra.isEmpty();
        if (thingFromSignal.isPresent() && hasExtra) {
            // merge
            final Thing baseThing = thingFromSignal.get();
            final JsonObject baseThingJson = baseThing.toJson(baseThing.getImplementedSchemaVersion());
            thing = ThingsModelFactory.newThing(JsonFactory.newObject(baseThingJson, extra));
        } else if (thingFromSignal.isPresent()) {
            thing = thingFromSignal.get();
        } else if (hasExtra) {
            thing = ThingsModelFactory.newThing(extra);
        } else {
            // no information; there is no thing.
            return Optional.empty();
        }

        if (signal instanceof EventsourcedEvent) {
            return Optional.of(thing.toBuilder().setRevision(((EventsourcedEvent<?>) signal).getRevision()).build());
        }
        return Optional.of(thing);
    }

    private static Map<Class<?>, BiFunction<ThingEvent<?>, ThingBuilder.FromScratch, Thing>> createEventToThingMappers() {
        final Map<Class<?>, BiFunction<ThingEvent<?>, ThingBuilder.FromScratch, Thing>> mappers = new HashMap<>();

        mappers.put(ThingCreated.class,
                (te, tb) -> ((ThingCreated) te).getThing().toBuilder().setRevision(te.getRevision()).build());
        mappers.put(ThingModified.class,
                (te, tb) -> ((ThingModified) te).getThing().toBuilder().setRevision(te.getRevision()).build());
        mappers.put(ThingMerged.class,
                (te, tb) -> {
                    final ThingMerged thingMerged = (ThingMerged) te;
                    final JsonPointer resourcePath = thingMerged.getResourcePath();
                    final JsonValue nonNullValue = filterNullValuesInJsonValue(thingMerged.getValue());
                    final JsonObject mergedFields = JsonFactory.newObject(resourcePath, nonNullValue);
                    final JsonObject thingWithoutMergedFields = tb.build().toJson(FieldType.all());
                    final JsonValue mergedJson = JsonFactory.mergeJsonValues(thingWithoutMergedFields, mergedFields);
                    return ThingsModelFactory.newThing(mergedJson.asObject());
                }
        );
        mappers.put(ThingDeleted.class,
                (te, tb) -> tb.build());

        mappers.put(PolicyIdModified.class,
                (te, tb) -> tb.setPolicyId(((PolicyIdModified) te).getPolicyEntityId()).build());

        mappers.put(AttributesCreated.class,
                (te, tb) -> tb.setAttributes(((AttributesCreated) te).getCreatedAttributes()).build());
        mappers.put(AttributesModified.class,
                (te, tb) -> tb.setAttributes(((AttributesModified) te).getModifiedAttributes()).build());
        mappers.put(AttributesDeleted.class, (te, tb) -> tb.build());
        mappers.put(AttributeCreated.class, (te, tb) -> tb.setAttribute(((AttributeCreated) te).getAttributePointer(),
                ((AttributeCreated) te).getAttributeValue()).build());
        mappers.put(AttributeModified.class, (te, tb) -> tb.setAttribute(((AttributeModified) te).getAttributePointer(),
                ((AttributeModified) te).getAttributeValue()).build());
        mappers.put(AttributeDeleted.class, (te, tb) -> tb.build());

        mappers.put(ThingDefinitionCreated.class,
                (te, tb) -> tb.setDefinition(((ThingDefinitionCreated) te).getThingDefinition()).build());
        mappers.put(ThingDefinitionModified.class,
                (te, tb) -> tb.setDefinition(((ThingDefinitionModified) te).getThingDefinition()).build());
        mappers.put(ThingDefinitionDeleted.class, (te, tb) -> tb.build());

        mappers.put(FeaturesCreated.class, (te, tb) -> tb.setFeatures(((FeaturesCreated) te).getFeatures()).build());
        mappers.put(FeaturesModified.class, (te, tb) -> tb.setFeatures(((FeaturesModified) te).getFeatures()).build());
        mappers.put(FeaturesDeleted.class, (te, tb) -> tb.build());
        mappers.put(FeatureCreated.class, (te, tb) -> tb.setFeature(((FeatureCreated) te).getFeature()).build());
        mappers.put(FeatureModified.class, (te, tb) -> tb.setFeature(((FeatureModified) te).getFeature()).build());
        mappers.put(FeatureDeleted.class, (te, tb) -> tb.build());

        mappers.put(FeaturePropertiesCreated.class, (te, tb) -> tb.setFeature(Feature.newBuilder()
                .properties(((FeaturePropertiesCreated) te).getProperties())
                .withId(((FeaturePropertiesCreated) te).getFeatureId())
                .build()).build());
        mappers.put(FeaturePropertiesModified.class, (te, tb) -> tb.setFeature(Feature.newBuilder()
                .properties(((FeaturePropertiesModified) te).getProperties())
                .withId(((FeaturePropertiesModified) te).getFeatureId())
                .build()).build());
        mappers.put(FeaturePropertiesDeleted.class, (te, tb) -> tb.build());
        mappers.put(FeaturePropertyCreated.class, (te, tb) ->
                tb.setFeatureProperty(((FeaturePropertyCreated) te).getFeatureId(),
                        ((FeaturePropertyCreated) te).getPropertyPointer(),
                        ((FeaturePropertyCreated) te).getPropertyValue()).build());
        mappers.put(FeaturePropertyModified.class, (te, tb) ->
                tb.setFeatureProperty(((FeaturePropertyModified) te).getFeatureId(),
                        ((FeaturePropertyModified) te).getPropertyPointer(),
                        ((FeaturePropertyModified) te).getPropertyValue()).build());
        mappers.put(FeaturePropertyDeleted.class, (te, tb) -> tb.build());

        return mappers;
    }

    private static JsonValue filterNullValuesInJsonValue(final JsonValue value) {
        final JsonValue result;
        if (value.isObject()) {
            result = filterNullValuesInObject(value.asObject());
        } else if (value.isArray()) {
            result = value.asArray();
        } else {
            if (value.isNull()) {
                result = JsonObject.empty();
            } else {
                result = value;
            }
        }

        return result;
    }

    private static JsonValue filterNullValuesInObject(final JsonObject jsonObject) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        jsonObject.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value = jsonField.getValue();
            final JsonValue result;

            if (value.isNull()) {
                return;
            } else if (value.isObject()) {
                result = filterNullValuesInObject(value.asObject());
            } else {
                result = value;
            }
            builder.set(key, result);
        });

        return builder.build();
    }
}
