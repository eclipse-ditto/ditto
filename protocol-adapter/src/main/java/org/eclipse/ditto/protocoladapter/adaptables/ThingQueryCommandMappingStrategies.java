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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
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

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing query commands.
 */
final class ThingQueryCommandMappingStrategies extends AbstractThingMappingStrategies<ThingQueryCommand<?>> {

    private static final ThingQueryCommandMappingStrategies INSTANCE = new ThingQueryCommandMappingStrategies();

    private ThingQueryCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    public static ThingQueryCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<ThingQueryCommand<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<ThingQueryCommand<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(RetrieveThing.TYPE, adaptable -> RetrieveThing.getBuilder(thingIdFrom(adaptable),
                dittoHeadersFrom(adaptable))
                .withSelectedFields(selectedFieldsFrom(adaptable))
                .build());

        mappingStrategies.put(RetrieveThings.TYPE, adaptable -> RetrieveThings.getBuilder(thingsIdsFrom(adaptable))
                .dittoHeaders(dittoHeadersFrom(adaptable))
                .namespace(namespaceFrom(adaptable))
                .selectedFields(selectedFieldsFrom(adaptable)).build());

        mappingStrategies.put(RetrieveAcl.TYPE, adaptable -> RetrieveAcl.of(thingIdFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclEntry.TYPE, adaptable -> RetrieveAclEntry.of(thingIdFrom(adaptable),
                authorizationSubjectFrom(adaptable), selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributes.TYPE, adaptable -> RetrieveAttributes.of(thingIdFrom(adaptable),
                selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttribute.TYPE, adaptable -> RetrieveAttribute.of(thingIdFrom(adaptable),
                attributePointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveThingDefinition.TYPE,
                adaptable -> RetrieveThingDefinition.of(thingIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatures.TYPE, adaptable -> RetrieveFeatures.of(thingIdFrom(adaptable),
                selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeature.TYPE, adaptable -> RetrieveFeature.of(thingIdFrom(adaptable),
                featureIdFrom(adaptable), selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureDefinition.TYPE, adaptable ->
                RetrieveFeatureDefinition.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureProperties.TYPE, adaptable ->
                RetrieveFeatureProperties.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureProperty.TYPE, adaptable ->
                RetrieveFeatureProperty.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static List<ThingId> thingsIdsFrom(final Adaptable adaptable) {
        final JsonArray array = adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseThrow(() -> new JsonParseException("Adaptable payload was non existing or no JsonObject"))
                .getValue(RetrieveThings.JSON_THING_IDS)
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .orElseThrow(() -> new JsonParseException("Could not map 'thingIds' value to expected JsonArray"));

        return array.stream()
                .map(JsonValue::asString)
                .map(ThingId::of)
                .collect(Collectors.toList());
    }

}
