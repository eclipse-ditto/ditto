/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.UnsupportedMediaTypeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotDeletableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotDeletableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingMergeInvalidException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;

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
                ThingMergeCommandMappingStrategies::mergeThingWithFeatureDesiredProperties);
        mappingStrategies.put("featureDesiredProperty",
                ThingMergeCommandMappingStrategies::mergeThingWithFeatureDesiredProperty);
        return mappingStrategies;
    }

    private static MergeThing mergeThing(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withThing(thingIdFrom(adaptable), thingForMergeFrom(adaptable), dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithPolicyId(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withPolicyId(thingIdFrom(adaptable), policyIdForMergeFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithThingDefinition(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withThingDefinition(thingIdFrom(adaptable),
                thingDefinitionFromForPatch(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithAttributes(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withAttributes(thingIdFrom(adaptable),
                attributesForMergeFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithAttribute(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withAttribute(thingIdFrom(adaptable),
                attributePointerFrom(adaptable),
                attributeValueFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatures(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeatures(thingIdFrom(adaptable),
                featuresFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeature(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeature(thingIdFrom(adaptable),
                featureFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureDefinition(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeatureDefinition(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featureDefinitionFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureProperties(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeatureProperties(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertiesFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureProperty(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeatureProperty(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertyPointerFrom(adaptable),
                featurePropertyValueFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureDesiredProperties(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeatureDesiredProperties(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertiesFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static MergeThing mergeThingWithFeatureDesiredProperty(final Adaptable adaptable) {
        checkContentTypeHeader(adaptable.getDittoHeaders());
        return MergeThing.withFeatureDesiredProperty(thingIdFrom(adaptable),
                featureIdFrom(adaptable),
                featurePropertyPointerFrom(adaptable),
                featurePropertyValueFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    protected static PolicyId policyIdForMergeFrom(final Adaptable adaptable) {
        if (payloadValueIsNull(adaptable)) {
            throw PolicyIdNotDeletableException.newBuilder().build();
        }
        return policyIdFrom(adaptable);
    }

    protected static ThingDefinition thingDefinitionFromForPatch(final Adaptable adaptable) {
        if (payloadValueIsNull(adaptable)) {
            return ThingsModelFactory.nullDefinition();
        } else {
            return thingDefinitionFrom(adaptable);
        }
    }

    protected static Thing thingForMergeFrom(final Adaptable adaptable) {
        if (payloadValueIsNull(adaptable)
                && adaptable.getDittoHeaders().getSchemaVersion().filter(JsonSchemaVersion.V_2::equals).isPresent()) {
            throw ThingMergeInvalidException.fromMessage(
                    "The provided json value can not be applied at this resource", adaptable.getDittoHeaders());
        }
        if (fieldIsNull(adaptable, Thing.JsonFields.ID.getPointer())) {
            throw ThingIdNotDeletableException.newBuilder().build();
        }
        if (fieldIsNull(adaptable, Thing.JsonFields.POLICY_ID.getPointer())) {
            throw PolicyIdNotDeletableException.newBuilder().build();
        }
        return thingFrom(adaptable);
    }

    protected static Attributes attributesForMergeFrom(final Adaptable adaptable) {
        if (fieldIsNull(adaptable, Thing.JsonFields.ATTRIBUTES.getPointer())) {
            return ThingsModelFactory.nullAttributes();
        }
        return attributesFrom(adaptable);
    }

    private static boolean fieldIsNull(final Adaptable adaptable, final JsonPointer pointer) {
        return adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(pointer))
                .map(JsonValue::isNull)
                .orElse(false);
    }

    private static boolean payloadValueIsNull(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().map(JsonValue::isNull).orElse(false);
    }

    private static void checkContentTypeHeader(final DittoHeaders dittoHeaders) {
        final Optional<String> contentType = dittoHeaders.getContentType();
        if (contentType.isPresent()) {
            final String requestsMediaType = contentType.get();

            if (!contentType.map(ContentType::of).filter(ContentType::isJsonMergePatch).isPresent()){
                throw UnsupportedMediaTypeException
                        .builderForMergePatchJsonMediaType(requestsMediaType, ContentType.APPLICATION_MERGE_PATCH_JSON.getValue())
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        } else {
            throw UnsupportedMediaTypeException
                    .builderForEmptyContentTypeHeader(ContentType.APPLICATION_MERGE_PATCH_JSON.getValue())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
