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
package org.eclipse.ditto.services.things.persistence.actors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

/**
 * Provides methods to get command responses that include the correct eTag header value.
 */
public abstract class ETagTestUtils {

    private ETagTestUtils() {}

    // Thing

    public static RetrieveThingResponse retrieveThingResponse(final Thing expectedThing,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThing, dittoHeaders);
        return RetrieveThingResponse.of(expectedThing.getId().get(), expectedJsonRepresentation, dittoHeadersWithETag);
    }

    public static RetrieveThingResponse retrieveThingResponse(final Thing expectedThing,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThing, dittoHeaders);
        return RetrieveThingResponse.of(expectedThing.getId().get(), expectedThing, dittoHeadersWithETag);
    }

    public static ModifyThingResponse modifyThingResponse(final Thing currentThing, final Thing modifiedThing,
            final DittoHeaders dittoHeaders, final boolean created) {
        final Thing modifiedThingWithUpdatedRevision = modifiedThing.toBuilder()
                .setRevision(currentThing.getRevision().get().toLong() + 1)
                .build();
        final DittoHeaders dittoHeadersWithETag =
                appendETagToDittoHeaders(modifiedThingWithUpdatedRevision, dittoHeaders);
        return ModifyThingResponse.modified(modifiedThing.getId().get(), dittoHeadersWithETag);
    }

    public static SudoRetrieveThingResponse sudoRetrieveThingResponse(final Thing expectedThing,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedThing, dittoHeaders);
        return SudoRetrieveThingResponse.of(expectedJsonRepresentation, dittoHeadersWithETag);
    }

    // Features

    public static ModifyFeaturesResponse modifyFeaturesResponse(final String thingId, final Features modifiedFeatures,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(modifiedFeatures, dittoHeaders);
        if (created) {
            return ModifyFeaturesResponse.created(thingId, modifiedFeatures, dittoHeadersWithETag);
        } else {
            return ModifyFeaturesResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeaturesResponse retrieveFeaturesResponse(final String thingId,
            final Features expectedFeatures,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeatures, dittoHeaders);
        return RetrieveFeaturesResponse.of(thingId, expectedJsonRepresentation, dittoHeadersWithETag);
    }

    // Feature

    public static RetrieveFeatureResponse retrieveFeatureResponse(String thingId, final Feature expectedFeature,
            final JsonObject expectedJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeature, dittoHeaders);
        return RetrieveFeatureResponse.of(thingId, expectedFeature.getId(), expectedJsonRepresentation,
                dittoHeadersWithETag);
    }

    public static ModifyFeatureResponse modifyFeatureResponse(final String thingId, final Feature feature,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(feature, dittoHeaders);
        if (created) {
            return ModifyFeatureResponse.created(thingId, feature, dittoHeadersWithETag);
        } else {
            return ModifyFeatureResponse.modified(thingId, feature.getId(), dittoHeadersWithETag);
        }
    }

    // FeatureDefinition

    public static ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponse(final String thingId,
            final String featureId, final FeatureDefinition definition, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(definition, dittoHeaders);
        if (created) {
            return ModifyFeatureDefinitionResponse.created(thingId, featureId, definition, dittoHeadersWithETag);
        } else {
            return ModifyFeatureDefinitionResponse.modified(thingId, featureId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeatureDefinitionResponse retrieveFeatureDefinitionResponse(final String thingId,
            final String featureId, final FeatureDefinition expectedFeatureDefinition,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeatureDefinition, dittoHeaders);
        return RetrieveFeatureDefinitionResponse.of(thingId, featureId, expectedFeatureDefinition,
                dittoHeadersWithETag);
    }

    // FeatureProperties

    public static ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponse(final String thingId,
            final String featureId, final FeatureProperties properties, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(properties, dittoHeaders);
        if (created) {
            return ModifyFeaturePropertiesResponse.created(thingId, featureId, properties, dittoHeadersWithETag);
        } else {
            return ModifyFeaturePropertiesResponse.modified(thingId, featureId, dittoHeadersWithETag);
        }
    }

    public static RetrieveFeaturePropertiesResponse retrieveFeaturePropertiesResponse(final String thingId,
            final String featureId, final FeatureProperties expectedFeatureProperties,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedFeatureProperties, dittoHeaders);
        return RetrieveFeaturePropertiesResponse.of(thingId, featureId, expectedFeatureProperties,
                dittoHeadersWithETag);
    }

    // FeatureProperty

    public static ModifyFeaturePropertyResponse modifyFeaturePropertyResponse(final String thingId,
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

    public static RetrieveFeaturePropertyResponse retrieveFeaturePropertyResponse(final String thingId,
            final String featureId, final JsonPointer propertyPointer, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(propertyValue, dittoHeaders);
        return RetrieveFeaturePropertyResponse.of(thingId, featureId, propertyPointer, propertyValue,
                dittoHeadersWithETag);
    }

    // Attributes

    public static ModifyAttributesResponse modifyAttributesResponse(final String thingId,
            final Attributes modifiedAttributes, final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(modifiedAttributes, dittoHeaders);
        if (created) {
            return ModifyAttributesResponse.created(thingId, modifiedAttributes, dittoHeadersWithETag);
        } else {
            return ModifyAttributesResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrieveAttributesResponse retrieveAttributesResponse(final String thingId,
            final Attributes expectedAttributes, final JsonObject expectedJsonRepresentation,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedAttributes, dittoHeaders);
        return RetrieveAttributesResponse.of(thingId, expectedJsonRepresentation, dittoHeadersWithETag);
    }

    // Attribute

    public static ModifyAttributeResponse modifyAttributeResponse(final String thingId,
            final JsonPointer attributePointer, final JsonValue attributeValue, final DittoHeaders dittoHeaders,
            final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(attributeValue, dittoHeaders);
        if (created) {
            return ModifyAttributeResponse.created(thingId, attributePointer, attributeValue, dittoHeadersWithETag);
        } else {
            return ModifyAttributeResponse.modified(thingId, attributePointer, dittoHeadersWithETag);
        }
    }

    public static RetrieveAttributeResponse retrieveAttributeResponse(final String thingId,
            final JsonPointer attributePointer, final JsonValue attributeValue, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(attributeValue, dittoHeaders);
        return RetrieveAttributeResponse.of(thingId, attributePointer, attributeValue, dittoHeadersWithETag);
    }

    // Acl

    public static ModifyAclResponse modifyAclResponse(final String thingId, final AccessControlList expectedAcl,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedAcl, dittoHeaders);
        if (created) {
            return ModifyAclResponse.created(thingId, expectedAcl, dittoHeadersWithETag);
        } else {
            return ModifyAclResponse.modified(thingId, expectedAcl, dittoHeadersWithETag);
        }
    }

    public static RetrieveAclResponse retrieveAclResponse(final String thingId, final AccessControlList expectedAcl,
            final JsonObject expectedAclJsonRepresentation, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedAcl, dittoHeaders);
        return RetrieveAclResponse.of(thingId, expectedAclJsonRepresentation, dittoHeadersWithETag);
    }

    // AclEntry

    public static ModifyAclEntryResponse modifyAclEntryResponse(final String thingId, final AclEntry expectedAclEntry,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedAclEntry, dittoHeaders);
        if (created) {
            return ModifyAclEntryResponse.created(thingId, expectedAclEntry, dittoHeadersWithETag);
        } else {
            return ModifyAclEntryResponse.modified(thingId, expectedAclEntry, dittoHeadersWithETag);
        }
    }

    public static RetrieveAclEntryResponse retrieveAclEntryResponse(final String thingId,
            final AclEntry expectedAclEntry, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedAclEntry, dittoHeaders);
        return RetrieveAclEntryResponse.of(thingId, expectedAclEntry, dittoHeadersWithETag);
    }


    // PolicyId

    public static ModifyPolicyIdResponse modifyPolicyIdResponse(final String thingId, final String policyId,
            final DittoHeaders dittoHeaders, final boolean created) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(policyId, dittoHeaders);
        if (created) {
            return ModifyPolicyIdResponse.created(thingId, policyId, dittoHeadersWithETag);
        } else {
            return ModifyPolicyIdResponse.modified(thingId, dittoHeadersWithETag);
        }
    }

    public static RetrievePolicyIdResponse retrievePolicyIdResponse(final String thingId,
            final String expectedPolicyId, final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(expectedPolicyId, dittoHeaders);
        return RetrievePolicyIdResponse.of(thingId, expectedPolicyId, dittoHeadersWithETag);
    }


    protected static DittoHeaders appendETagToDittoHeaders(final Object object, final DittoHeaders dittoHeaders) {

        return dittoHeaders.toBuilder().eTag(EntityTag.fromEntity(object).get()).build();
    }
}
