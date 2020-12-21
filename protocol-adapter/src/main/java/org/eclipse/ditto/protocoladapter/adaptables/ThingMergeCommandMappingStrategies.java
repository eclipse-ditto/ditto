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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing merge commands.
 */
final class ThingMergeCommandMappingStrategies extends AbstractThingMappingStrategies<MergeThing> {

    private static final ThingMergeCommandMappingStrategies INSTANCE = new ThingMergeCommandMappingStrategies();

    private ThingMergeCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static ThingMergeCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<MergeThing>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<MergeThing>> mappingStrategies = new HashMap<>();
        mappingStrategies.put("thing", ThingMergeCommandMappingStrategies::mergeThing);
        mappingStrategies.put("policyId", ThingMergeCommandMappingStrategies::mergeThingWithPolicyId);
        mappingStrategies.put("definition", ThingMergeCommandMappingStrategies::mergeThingWithThingDefinition);
        mappingStrategies.put("attributes", ThingMergeCommandMappingStrategies::mergeThingWithAttributes);
        mappingStrategies.put("attribute", ThingMergeCommandMappingStrategies::mergeThingWithAttribute);
        mappingStrategies.put("features", ThingMergeCommandMappingStrategies::mergeThingWithFeatures);
        mappingStrategies.put("feature", ThingMergeCommandMappingStrategies::mergeThingWithFeature);
        mappingStrategies.put("featureDefinition", ThingMergeCommandMappingStrategies::mergeThingWithFeatureDefinition);
        mappingStrategies.put("featureProperties", ThingMergeCommandMappingStrategies::mergeThingWithFeatureProperties);
        mappingStrategies.put("featureProperty", ThingMergeCommandMappingStrategies::mergeThingWithFeatureProperty);
        mappingStrategies.put("featureDesiredProperties",
                ThingMergeCommandMappingStrategies::mergeThingWithDesiredFeatureProperties);
        mappingStrategies.put("featureDesiredProperty",
                ThingMergeCommandMappingStrategies::mergeThingWithDesiredFeatureProperty);
        return mappingStrategies;
    }

    private static MergeThing mergeThing(final Adaptable adaptable) {
        return MergeThing.withThing(thingIdFrom(adaptable), thingFrom(adaptable), dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithPolicyId(final Adaptable adaptable) {
        return MergeThing.withPolicyId(thingIdFrom(adaptable), policyIdFrom(adaptable), dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithThingDefinition(final Adaptable adaptable) {
        return MergeThing.withThingDefinition(thingIdFrom(adaptable),
                thingDefinitionFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithAttributes(final Adaptable adaptable) {
        return MergeThing.withAttributes(thingIdFrom(adaptable),
                attributesFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithAttribute(final Adaptable adaptable) {
        return MergeThing.withAttribute(thingIdFrom(adaptable),
                attributePointerFrom(adaptable),
                adaptable.getPayload().getValue().orElse(JsonValue.of(null)),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatures(final Adaptable adaptable) {
        return MergeThing.withFeatures(thingIdFrom(adaptable),
                featuresFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeature(final Adaptable adaptable) {
        return MergeThing.withFeature(thingIdFrom(adaptable),
                featureFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureDefinition(final Adaptable adaptable) {
        return MergeThing.withFeatureDefinition(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featureDefinitionFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureProperties(final Adaptable adaptable) {
        return MergeThing.withFeatureProperties(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertiesFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureProperty(final Adaptable adaptable) {
        return MergeThing.withFeatureProperty(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertyPointerFrom(adaptable),
                adaptable.getPayload().getValue().orElse(JsonValue.of(null)),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithDesiredFeatureProperties(final Adaptable adaptable) {
        return MergeThing.withDesiredFeatureProperties(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertiesFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithDesiredFeatureProperty(final Adaptable adaptable) {
        return MergeThing.withDesiredFeatureProperty(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertyPointerFrom(adaptable),
                adaptable.getPayload().getValue().orElse(JsonValue.of(null)),
                dittoHeadersFrom(adaptable));
    }

}
