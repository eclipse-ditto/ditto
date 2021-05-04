/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;
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

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing events.
 */
final class ThingEventMappingStrategies extends AbstractThingMappingStrategies<ThingEvent<?>> {

    private static final ThingEventMappingStrategies INSTANCE = new ThingEventMappingStrategies();

    private ThingEventMappingStrategies() {
        super(initMappingStrategies());
    }

    static ThingEventMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<ThingEvent<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies = new HashMap<>();
        addTopLevelEvents(mappingStrategies);
        addAttributeEvents(mappingStrategies);
        addDefinitionEvents(mappingStrategies);
        addFeatureEvents(mappingStrategies);
        addFeatureDefinitionEvents(mappingStrategies);
        addFeaturePropertyEvents(mappingStrategies);
        addFeatureDesiredPropertyEvents(mappingStrategies);
        addPolicyIdEvents(mappingStrategies);
        return mappingStrategies;
    }

    private static void addTopLevelEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(ThingCreated.TYPE,
                adaptable -> ThingCreated.of(thingFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(ThingModified.TYPE,
                adaptable -> ThingModified.of(thingFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(ThingDeleted.TYPE,
                adaptable -> ThingDeleted.of(thingIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addPolicyIdEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(PolicyIdModified.TYPE,
                adaptable -> PolicyIdModified.of(thingIdFrom(adaptable),
                        policyIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addFeaturePropertyEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(FeaturePropertiesCreated.TYPE,
                adaptable -> FeaturePropertiesCreated.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertiesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeaturePropertiesModified.TYPE,
                adaptable -> FeaturePropertiesModified.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertiesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeaturePropertiesDeleted.TYPE,
                adaptable -> FeaturePropertiesDeleted.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));

        mappingStrategies.put(FeaturePropertyCreated.TYPE,
                adaptable -> FeaturePropertyCreated.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        featurePropertyValueFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeaturePropertyModified.TYPE,
                adaptable -> FeaturePropertyModified.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        featurePropertyValueFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeaturePropertyDeleted.TYPE,
                adaptable -> FeaturePropertyDeleted.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addFeatureDesiredPropertyEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(FeatureDesiredPropertiesCreated.TYPE,
                adaptable -> FeatureDesiredPropertiesCreated.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertiesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDesiredPropertiesModified.TYPE,
                adaptable -> FeatureDesiredPropertiesModified.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertiesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDesiredPropertiesDeleted.TYPE,
                adaptable -> FeatureDesiredPropertiesDeleted.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));

        mappingStrategies.put(FeatureDesiredPropertyCreated.TYPE,
                adaptable -> FeatureDesiredPropertyCreated.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        featurePropertyValueFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDesiredPropertyModified.TYPE,
                adaptable -> FeatureDesiredPropertyModified.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        featurePropertyValueFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDesiredPropertyDeleted.TYPE,
                adaptable -> FeatureDesiredPropertyDeleted.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }


    private static void addFeatureDefinitionEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(FeatureDefinitionCreated.TYPE,
                adaptable -> FeatureDefinitionCreated.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featureDefinitionFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDefinitionModified.TYPE,
                adaptable -> FeatureDefinitionModified.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featureDefinitionFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDefinitionDeleted.TYPE,
                adaptable -> FeatureDefinitionDeleted.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addFeatureEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(FeaturesCreated.TYPE,
                adaptable -> FeaturesCreated.of(thingIdFrom(adaptable),
                        featuresFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeaturesModified.TYPE,
                adaptable -> FeaturesModified.of(thingIdFrom(adaptable),
                        featuresFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeaturesDeleted.TYPE,
                adaptable -> FeaturesDeleted.of(thingIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));

        mappingStrategies.put(FeatureCreated.TYPE,
                adaptable -> FeatureCreated.of(thingIdFrom(adaptable),
                        featureFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureModified.TYPE,
                adaptable -> FeatureModified.of(thingIdFrom(adaptable),
                        featureFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(FeatureDeleted.TYPE,
                adaptable -> FeatureDeleted.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addDefinitionEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(ThingDefinitionCreated.TYPE,
                adaptable -> ThingDefinitionCreated.of(thingIdFrom(adaptable),
                        thingDefinitionFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(ThingDefinitionModified.TYPE,
                adaptable -> ThingDefinitionModified.of(thingIdFrom(adaptable),
                        thingDefinitionFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(ThingDefinitionDeleted.TYPE,
                adaptable -> ThingDefinitionDeleted.of(thingIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addAttributeEvents(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies) {
        mappingStrategies.put(AttributesCreated.TYPE,
                adaptable -> AttributesCreated.of(thingIdFrom(adaptable),
                        attributesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(AttributesModified.TYPE,
                adaptable -> AttributesModified.of(thingIdFrom(adaptable),
                        attributesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(AttributesDeleted.TYPE,
                adaptable -> AttributesDeleted.of(thingIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));

        mappingStrategies.put(AttributeCreated.TYPE,
                adaptable -> AttributeCreated.of(thingIdFrom(adaptable),
                        attributePointerFrom(adaptable),
                        attributeValueFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(AttributeModified.TYPE,
                adaptable -> AttributeModified.of(thingIdFrom(adaptable),
                        attributePointerFrom(adaptable),
                        attributeValueFrom(adaptable), revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(AttributeDeleted.TYPE,
                adaptable -> AttributeDeleted.of(thingIdFrom(adaptable),
                        attributePointerFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static long revisionFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getRevision().orElseThrow(() -> JsonMissingFieldException.newBuilder()
                .fieldName(Payload.JsonFields.REVISION.getPointer().toString()).build());
    }

    @Nullable
    private static Instant timestampFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getTimestamp().orElse(null);
    }

    @Nullable
    private static Metadata metadataFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getMetadata().orElse(null);
    }

}
