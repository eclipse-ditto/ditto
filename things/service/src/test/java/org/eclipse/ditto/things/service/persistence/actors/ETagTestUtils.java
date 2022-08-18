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
package org.eclipse.ditto.things.service.persistence.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.Revision;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;

/**
 * Provides methods to get command responses that include the correct eTag header value.
 */
public final class ETagTestUtils {

    private ETagTestUtils() {
        throw new AssertionError();
    }

    // Thing

    public static RetrieveThingResponse retrieveThingResponse(final Thing expectedThing,
            @Nullable final JsonFieldSelector thingFieldSelector, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThing, dittoHeaders);
        return RetrieveThingResponse.of(expectedThing.getEntityId().get(), expectedThing, thingFieldSelector, null,
                dittoHeadersWithETag);
    }

    public static RetrieveThingResponse retrieveThingResponse(final Thing expectedThing,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThing, dittoHeaders);
        return RetrieveThingResponse.of(expectedThing.getEntityId().get(),
                expectedThing.toJsonString(dittoHeaders.getSchemaVersion().get()), dittoHeadersWithETag);
    }

    public static ModifyThingResponse modifyThingResponse(final Thing currentThing, final Thing modifiedThing,
            final DittoHeaders dittoHeaders, final boolean created) {
        final Thing modifiedThingWithUpdatedRevision = modifiedThing.toBuilder()
                .setRevision(currentThing.getRevision()
                        .map(Revision::increment)
                        .orElseGet(() -> ThingRevision.newInstance(1L)))
                .build();
        final DittoHeaders dittoHeadersWithETag =
                appendETagToDittoHeaders(modifiedThingWithUpdatedRevision, dittoHeaders);
        return ModifyThingResponse.modified(modifiedThing.getEntityId().get(), dittoHeadersWithETag);
    }

    public static MergeThingResponse mergeThingResponse(final Thing currentThing, final JsonPointer path,
            final DittoHeaders dittoHeaders) {
        final Thing modifiedThingWithUpdatedRevision = currentThing.toBuilder()
                .setRevision(currentThing.getRevision()
                        .map(Revision::increment)
                        .orElseGet(() -> ThingRevision.newInstance(1L)))
                .build();
        final DittoHeaders dittoHeadersWithETag =
                appendETagToDittoHeaders(modifiedThingWithUpdatedRevision, dittoHeaders);
        final ThingId thingId = currentThing.getEntityId().orElseThrow();
        return MergeThingResponse.of(thingId, path, dittoHeadersWithETag);
    }

    public static SudoRetrieveThingResponse sudoRetrieveThingResponse(final Thing expectedThing,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThing, dittoHeaders);
        return SudoRetrieveThingResponse.of(expectedJsonRepresentation, dittoHeadersWithETag);
    }

    // Features

    public static ModifyFeaturesResponse modifyFeaturesResponse(final ThingId thingId, final Features modifiedFeatures,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(modifiedFeatures, dittoHeaders);
        if (created) {
            return ModifyFeaturesResponse.created(thingId, modifiedFeatures, dittoHeadersWithETag);
        } else {
            return ModifyFeaturesResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeaturesResponse retrieveFeaturesResponse(final ThingId thingId,
            final Features expectedFeatures,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeatures, dittoHeaders);
        return RetrieveFeaturesResponse.of(thingId, expectedJsonRepresentation, dittoHeadersWithETag);
    }

    // Feature

    public static RetrieveFeatureResponse retrieveFeatureResponse(final ThingId thingId, final Feature expectedFeature,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeature, dittoHeaders);
        return RetrieveFeatureResponse.of(thingId, expectedFeature.getId(), expectedJsonRepresentation,
                dittoHeadersWithETag);
    }

    public static ModifyFeatureResponse modifyFeatureResponse(final ThingId thingId, final Feature feature,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(feature, dittoHeaders);
        if (created) {
            return ModifyFeatureResponse.created(thingId, feature, dittoHeadersWithETag);
        } else {
            return ModifyFeatureResponse.modified(thingId, feature.getId(), dittoHeadersWithETag);
        }
    }

    // FeatureDefinition

    public static ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponse(final ThingId thingId,
            final String featureId, final FeatureDefinition definition, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(definition, dittoHeaders);
        if (created) {
            return ModifyFeatureDefinitionResponse.created(thingId, featureId, definition, dittoHeadersWithETag);
        } else {
            return ModifyFeatureDefinitionResponse.modified(thingId, featureId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeatureDefinitionResponse retrieveFeatureDefinitionResponse(final ThingId thingId,
            final String featureId, final FeatureDefinition expectedFeatureDefinition,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeatureDefinition, dittoHeaders);
        return RetrieveFeatureDefinitionResponse.of(thingId, featureId, expectedFeatureDefinition,
                dittoHeadersWithETag);
    }

    // FeatureProperties

    public static ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponse(final ThingId thingId,
            final String featureId, final FeatureProperties properties, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(properties, dittoHeaders);
        if (created) {
            return ModifyFeaturePropertiesResponse.created(thingId, featureId, properties, dittoHeadersWithETag);
        } else {
            return ModifyFeaturePropertiesResponse.modified(thingId, featureId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeaturePropertiesResponse retrieveFeaturePropertiesResponse(final ThingId thingId,
            final String featureId, final FeatureProperties featureProperties,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(featureProperties, dittoHeaders);
        return RetrieveFeaturePropertiesResponse.of(thingId, featureId, featureProperties,
                dittoHeadersWithETag);
    }

    public static RetrieveFeaturePropertiesResponse retrieveFeaturePropertiesResponse(final ThingId thingId,
            final String featureId, final FeatureProperties featureProperties,
            final FeatureProperties expectedFeatureProperties,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(featureProperties, dittoHeaders);
        return RetrieveFeaturePropertiesResponse.of(thingId, featureId, expectedFeatureProperties,
                dittoHeadersWithETag);
    }

    // FeatureDesiredProperties

    public static ModifyFeatureDesiredPropertiesResponse modifyFeatureDesiredPropertiesResponse(final ThingId thingId,
            final String featureId, final FeatureProperties properties, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(properties, dittoHeaders);
        if (created) {
            return ModifyFeatureDesiredPropertiesResponse.created(thingId, featureId, properties, dittoHeadersWithETag);
        } else {
            return ModifyFeatureDesiredPropertiesResponse.modified(thingId, featureId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeatureDesiredPropertiesResponse retrieveFeatureDesiredPropertiesResponse(
            final ThingId thingId,
            final String featureId, final FeatureProperties featureProperties,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(featureProperties, dittoHeaders);
        return RetrieveFeatureDesiredPropertiesResponse.of(thingId, featureId, featureProperties,
                dittoHeadersWithETag);
    }

    public static RetrieveFeatureDesiredPropertiesResponse retrieveFeatureDesiredPropertiesResponse(
            final ThingId thingId,
            final String featureId, final FeatureProperties featureProperties,
            final FeatureProperties expectedFeatureProperties,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(featureProperties, dittoHeaders);
        return RetrieveFeatureDesiredPropertiesResponse.of(thingId, featureId, expectedFeatureProperties,
                dittoHeadersWithETag);
    }

    // FeatureProperty

    public static ModifyFeaturePropertyResponse modifyFeaturePropertyResponse(final ThingId thingId,
            final String featureId, final JsonPointer propertyPointer, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(propertyValue, dittoHeaders);
        if (created) {
            return ModifyFeaturePropertyResponse.created(thingId, featureId, propertyPointer, propertyValue,
                    dittoHeadersWithETag);
        } else {
            return ModifyFeaturePropertyResponse.modified(thingId, featureId, propertyPointer, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeaturePropertyResponse retrieveFeaturePropertyResponse(final ThingId thingId,
            final String featureId, final JsonPointer propertyPointer, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(propertyValue, dittoHeaders);
        return RetrieveFeaturePropertyResponse.of(thingId, featureId, propertyPointer, propertyValue,
                dittoHeadersWithETag);
    }

    // FeatureDesiredProperty

    public static ModifyFeatureDesiredPropertyResponse modifyFeatureDesiredPropertyResponse(final ThingId thingId,
            final String featureId, final JsonPointer propertyPointer, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(propertyValue, dittoHeaders);
        if (created) {
            return ModifyFeatureDesiredPropertyResponse.created(thingId, featureId, propertyPointer, propertyValue,
                    dittoHeadersWithETag);
        } else {
            return ModifyFeatureDesiredPropertyResponse.modified(thingId, featureId, propertyPointer,
                    dittoHeadersWithETag);
        }
    }

    public static RetrieveFeatureDesiredPropertyResponse retrieveFeatureDesiredPropertyResponse(final ThingId thingId,
            final String featureId, final JsonPointer propertyPointer, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(propertyValue, dittoHeaders);
        return RetrieveFeatureDesiredPropertyResponse.of(thingId, featureId, propertyPointer, propertyValue,
                dittoHeadersWithETag);
    }

    // Attributes

    public static ModifyAttributesResponse modifyAttributeResponse(final ThingId thingId,
            final Attributes modifiedAttributes, final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(modifiedAttributes, dittoHeaders);
        if (created) {
            return ModifyAttributesResponse.created(thingId, modifiedAttributes, dittoHeadersWithETag);
        } else {
            return ModifyAttributesResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrieveAttributesResponse retrieveAttributesResponse(final ThingId thingId,
            final Attributes expectedAttributes, final JsonObject expectedJsonRepresentation,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedAttributes, dittoHeaders);
        return RetrieveAttributesResponse.of(thingId, expectedJsonRepresentation, dittoHeadersWithETag);
    }

    // Attribute

    public static ModifyAttributeResponse modifyAttributeResponse(final ThingId thingId,
            final JsonPointer attributePointer, final JsonValue attributeValue, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(attributeValue, dittoHeaders);
        if (created) {
            return ModifyAttributeResponse.created(thingId, attributePointer, attributeValue, dittoHeadersWithETag);
        } else {
            return ModifyAttributeResponse.modified(thingId, attributePointer, dittoHeadersWithETag);
        }
    }

    public static RetrieveAttributeResponse retrieveAttributeResponse(final ThingId thingId,
            final JsonPointer attributePointer, final JsonValue attributeValue, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(attributeValue, dittoHeaders);
        return RetrieveAttributeResponse.of(thingId, attributePointer, attributeValue, dittoHeadersWithETag);
    }

    // PolicyId

    public static ModifyPolicyIdResponse modifyPolicyIdResponse(final ThingId thingId, final PolicyId policyId,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(policyId, dittoHeaders);
        if (created) {
            return ModifyPolicyIdResponse.created(thingId, policyId, dittoHeadersWithETag);
        } else {
            return ModifyPolicyIdResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrievePolicyIdResponse retrievePolicyIdResponse(final ThingId thingId,
            final PolicyId expectedPolicyId, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedPolicyId, dittoHeaders);
        return RetrievePolicyIdResponse.of(thingId, expectedPolicyId, dittoHeadersWithETag);
    }

    // ThingDefinition

    public static ModifyThingDefinitionResponse modifyThingDefinitionResponse(final ThingId thingId,
            final ThingDefinition definition,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(definition, dittoHeaders);
        if (created) {
            return ModifyThingDefinitionResponse.created(thingId, definition, dittoHeadersWithETag);
        } else {
            return ModifyThingDefinitionResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrieveThingDefinitionResponse retrieveDefinitionResponse(final ThingId thingId,
            final ThingDefinition expectedThingDefinition, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThingDefinition, dittoHeaders);
        return RetrieveThingDefinitionResponse.of(thingId, expectedThingDefinition, dittoHeadersWithETag);
    }


    protected static DittoHeaders appendETagToDittoHeaders(final Object object, final DittoHeaders dittoHeaders) {

        return dittoHeaders.toBuilder().eTag(EntityTag.fromEntity(object).get()).build();
    }
}
