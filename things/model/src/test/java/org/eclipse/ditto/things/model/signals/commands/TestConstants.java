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
package org.eclipse.ditto.things.model.signals.commands;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.DittoSystemProperties;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributeNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributesNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDefinitionNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDesiredPropertiesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDesiredPropertiesNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDesiredPropertyNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDesiredPropertyNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturePropertiesNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturePropertyNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturesNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.MetadataNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.MissingThingIdsException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConflictException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingDefinitionNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotDeletableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingPreconditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingPreconditionNotModifiedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingTooManyModifyingRequestsException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    public static final long THING_SIZE_LIMIT_BYTES = Long.parseLong(
            System.getProperty(DittoSystemProperties.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES, "-1"));

    /**
     * A known correlation id for testing.
     */
    public static final String CORRELATION_ID = "a780b7b5-fdd2-4864-91fc-80df6bb0a636";

    /**
     * Known command headers.
     */
    public static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId(CORRELATION_ID)
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance("the_subject"),
                    AuthorizationSubject.newInstance("another_subject")))
            .build();

    /**
     * Empty command headers.
     */
    public static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /**
     * A known timestamp.
     */
    public static final Instant TIMESTAMP = Instant.EPOCH;

    /**
     * Known JSON parse options.
     */
    public static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES =
            JsonFactory.newFieldSelector("attributes(location,maker)", JSON_PARSE_OPTIONS);

    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES_WITH_THING_ID =
            JsonFactory.newFieldSelector("thingId,attributes(location,maker)", JSON_PARSE_OPTIONS);

    /**
     * A known JSON field selector.
     */
    public static final JsonFieldSelector JSON_FIELD_SELECTOR_FEATURE_PROPERTIES =
            JsonFactory.newFieldSelector("properties/target_year_1", JSON_PARSE_OPTIONS);

    /**
     * A known JsonPointer.
     */
    public static final JsonPointer PATH = JsonPointer.of("attributes");

    /**
     * A known JsonValue.
     */
    public static final JsonValue VALUE = JsonObject.empty();

    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Thing-related test constants.
     */
    public static final class Thing {

        /**
         * A known Thing ID for testing.
         */
        public static final ThingId THING_ID = ThingId.of("example.com", "testThing");

        /**
         * A known PolicyID of a Thing.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("example.com:testPolicy");

        /**
         * A known Definition of a Thing.
         */
        public static final ThingDefinition DEFINITION = ThingsModelFactory.newDefinition("example:test" +
                ":definition");

        /**
         * A known lifecycle of a Thing.
         */
        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        public static final JsonPointer LOCATION_ATTRIBUTE_POINTER = JsonFactory.newPointer("location");
        public static final JsonPointer ABSOLUTE_LOCATION_ATTRIBUTE_POINTER =
                org.eclipse.ditto.things.model.Thing.JsonFields.ATTRIBUTES.getPointer()
                        .append(LOCATION_ATTRIBUTE_POINTER);

        /**
         * A known location attribute for testing.
         */
        public static final JsonObject LOCATION_ATTRIBUTE_VALUE = JsonFactory.newObjectBuilder()
                .set("latitude", 44.673856)
                .set("longitude", 8.261719)
                .build();

        /**
         * A known location attribute for testing.
         */
        public static final JsonObject INVALID_ATTRIBUTE_VALUE = JsonFactory.newObjectBuilder()
                .set(Pointer.INVALID_JSON_POINTER, 44.673856)
                .set("longitude", 8.261719)
                .build();

        /**
         * Known attributes of a Thing.
         */
        public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
                .set(LOCATION_ATTRIBUTE_POINTER, LOCATION_ATTRIBUTE_VALUE)
                .set("maker", "Bosch")
                .build();

        /**
         * A known revision number of a Thing.
         */
        public static final long REVISION_NUMBER = 0;

        /**
         * A known revision of a Thing.
         */
        public static final ThingRevision REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);

        /**
         * A known Thing for testing.
         */
        public static final org.eclipse.ditto.things.model.Thing THING = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setDefinition(DEFINITION)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setPolicyId(POLICY_ID)
                .build();

        /**
         * A known {@code ThingConflictException}.
         */
        public static final ThingConflictException THING_CONFLICT_EXCEPTION =
                ThingConflictException.newBuilder(THING_ID).build();

        /**
         * A known {@code ThingIdNotExplicitlySettableException}.
         */
        public static final ThingIdNotExplicitlySettableException THING_ID_NOT_EXPLICITLY_SETTABLE_EXCEPTION =
                ThingIdNotExplicitlySettableException.forPostMethod().build();

        /**
         * A known {@code ThingPreconditionFailedException}.
         */
        public static final ThingPreconditionFailedException THING_PRECONDITION_FAILED_EXCEPTION =
                ThingPreconditionFailedException
                        .newBuilder(DittoHeaderDefinition.IF_MATCH.getKey(), "\"rev:1\"", "\"rev:2\"")
                        .build();

        /**
         * A known {@code ThingPreconditionNotModifiedException}.
         */
        public static final ThingPreconditionNotModifiedException THING_PRECONDITION_NOT_MODIFIED_EXCEPTION =
                ThingPreconditionNotModifiedException
                        .newBuilder("\"rev:1\"", "*")
                        .build();

        /**
         * A known {@code ThingNotAccessibleException}.
         */
        public static final ThingNotAccessibleException THING_NOT_ACCESSIBLE_EXCEPTION =
                ThingNotAccessibleException.newBuilder(THING_ID).build();

        /**
         * A known {@code ThingNotDeletableException}.
         */
        public static final ThingNotDeletableException THING_NOT_DELETABLE_EXCEPTION =
                ThingNotDeletableException.newBuilder(THING_ID).build();

        /**
         * A known {@code ThingNotModifiableException}.
         */
        public static final ThingNotCreatableException THING_NOT_CREATABLE_EXCEPTION =
                ThingNotCreatableException.newBuilderForPolicyMissing(THING_ID, POLICY_ID).build();

        /**
         * A known {@code ThingNotModifiableException}.
         */
        public static final ThingNotModifiableException THING_NOT_MODIFIABLE_EXCEPTION =
                ThingNotModifiableException.newBuilder(THING_ID).build();

        /**
         * A known {@code MetadataNotModifiableException}.
         */
        public static final MetadataNotModifiableException METADATA_NOT_MODIFIABLE_EXCEPTION =
                MetadataNotModifiableException.newBuilder().build();

        /**
         * A known {@code PolicyIdNotModifiableException}.
         */
        public static final PolicyIdNotModifiableException POLICY_ID_NOT_MODIFIABLE_EXCEPTION =
                PolicyIdNotModifiableException.newBuilder(THING_ID).build();

        /**
         * A known {@code PolicyIdNotAllowedException}.
         */
        public static final PolicyIdNotAllowedException POLICY_ID_NOT_ALLOWED_EXCEPTION =
                PolicyIdNotAllowedException.newBuilder(THING_ID).build();
        /**
         * A known {@code PolicyNotAllowedException}.
         */
        public static final PolicyNotAllowedException POLICY_NOT_ALLOWED_EXCEPTION =
                PolicyNotAllowedException.newBuilder(THING_ID).build();

        /**
         * A known {@code PolicyIdNotAccessibleException}.
         */
        public static final PolicyIdNotAccessibleException POLICY_ID_NOT_ACCESSIBLE_EXCEPTION =
                PolicyIdNotAccessibleException.newBuilder(THING_ID).build();

        /**
         * A known {@code ThingDefinitionNotAccessibleException}.
         */
        public static final ThingDefinitionNotAccessibleException THING_DEFINITION_NOT_ACCESSIBLE_EXCEPTION =
                ThingDefinitionNotAccessibleException.newBuilder(THING_ID).build();

        /**
         * A known {@code AttributesNotAccessibleException}.
         */
        public static final AttributesNotAccessibleException ATTRIBUTES_NOT_ACCESSIBLE_EXCEPTION =
                AttributesNotAccessibleException.newBuilder(THING_ID).build();
        /**
         * A known {@code AttributesNotModifiableException}.
         */
        public static final AttributesNotModifiableException ATTRIBUTES_NOT_MODIFIABLE_EXCEPTION =
                AttributesNotModifiableException.newBuilder(THING_ID).build();
        /**
         * A known {@code AttributeNotAccessibleException}.
         */
        public static final AttributeNotAccessibleException ATTRIBUTE_NOT_ACCESSIBLE_EXCEPTION =
                AttributeNotAccessibleException.newBuilder(THING_ID, LOCATION_ATTRIBUTE_POINTER).build();
        /**
         * A known {@code AttributeNotModifiableException}.
         */
        public static final AttributeNotModifiableException ATTRIBUTE_NOT_MODIFIABLE_EXCEPTION =
                AttributeNotModifiableException.newBuilder(THING_ID, LOCATION_ATTRIBUTE_POINTER).build();
        /**
         * A known {@code AttributePointerInvalidException}.
         */
        public static final AttributePointerInvalidException ATTRIBUTE_POINTER_INVALID_EXCEPTION =
                AttributePointerInvalidException.newBuilder(LOCATION_ATTRIBUTE_POINTER).build();
        /**
         * A known {@code ThingUnavailableException}.
         */
        public static final ThingUnavailableException THING_UNAVAILABLE_EXCEPTION =
                ThingUnavailableException.newBuilder(THING_ID).build();
        /**
         * A known {@code ThingTooManyModifyingRequestsException}.
         */
        public static final ThingTooManyModifyingRequestsException THING_TOO_MANY_MODIFYING_REQUESTS_EXCEPTION =
                ThingTooManyModifyingRequestsException.newBuilder(THING_ID).build();
        public static final MissingThingIdsException MISSING_THING_IDS_EXCEPTION =
                MissingThingIdsException.newBuilder().build();
        /**
         * List of required policy permissions for a Thing.
         */
        public static Collection<String> REQUIRED_THING_PERMISSIONS = Arrays.asList("READ", "WRITE");
        /**
         * A known {@code PolicyInvalidException}.
         */
        public static final PolicyInvalidException POLICY_INVALID_EXCEPTION =
                PolicyInvalidException.newBuilder(REQUIRED_THING_PERMISSIONS, THING_ID).build();

        /**
         * A known {@code ThingConditionFailedException}.
         */
        public static final ThingConditionFailedException THING_CONDITION_FAILED_EXCEPTION =
                ThingConditionFailedException.newBuilder(EMPTY_DITTO_HEADERS).build();

        /**
         * A known {@code ThingConditionInvalidException}.
         */
        public static final ThingConditionInvalidException THING_CONDITION_INVALID_EXCEPTION =
                ThingConditionInvalidException
                        .newBuilder("eq(attributes//attr1,42)", "")
                        .build();

        private Thing() {
            throw new AssertionError();
        }
    }

    /**
     * Feature-related test constants.
     */
    public static final class Feature {

        /**
         * A known ID of a Feature.
         */
        public static final String FLUX_CAPACITOR_ID = "FluxCapacitor";

        /**
         * Pointer of a known Feature Property.
         */
        public static final JsonPointer FLUX_CAPACITOR_PROPERTY_POINTER = JsonFactory.newPointer("target_year_1");

        /**
         * Value of a known Feature Property.
         */
        public static final JsonValue FLUX_CAPACITOR_PROPERTY_VALUE = JsonFactory.newValue(1955);

        /**
         * Value of a Feature Property with an invalid pointer.
         */
        public static final JsonValue FLUX_CAPACITOR_PROPERTY_VALUE_WITH_INVALID_POINTER =
                JsonFactory.newObjectBuilder()
                        .set(Pointer.INVALID_JSON_POINTER, JsonFactory.newValue(1955)).build();

        /**
         * Properties of a known Feature.
         */
        public static final FeatureDefinition FLUX_CAPACITOR_DEFINITION =
                FeatureDefinition.fromIdentifier("org.eclipse.ditto:fluxcapacitor:1.0.0");

        /**
         * Properties of a known Feature.
         */
        public static final FeatureProperties FLUX_CAPACITOR_PROPERTIES =
                ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("target_year_1", 1955)
                        .set("target_year_2", 2015)
                        .set("target_year_3", 1885)
                        .build();

        /**
         * A known Feature which is required for time travel.
         */
        public static final org.eclipse.ditto.things.model.Feature FLUX_CAPACITOR =
                ThingsModelFactory.newFeatureBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .definition(FLUX_CAPACITOR_DEFINITION)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();

        /**
         * A known ID of a Feature.
         */
        public static final String HOVER_BOARD_ID = "HoverBoard";

        /**
         * Pointer of a known Feature Property.
         */
        public static final JsonPointer HOVER_BOARD_PROPERTY_POINTER = JsonFactory.newPointer("speed");

        /**
         * Pointer of a known Feature desired Property.
         */
        public static final JsonPointer HOVER_BOARD_DESIRED_PROPERTY_POINTER = HOVER_BOARD_PROPERTY_POINTER;

        /**
         * Value of a known Feature property.
         */
        public static final JsonValue HOVER_BOARD_PROPERTY_VALUE = JsonFactory.newValue(21);

        /**
         * Value of a known Feature desired Property.
         */
        public static final JsonValue HOVER_BOARD_DESIRED_PROPERTY_VALUE = JsonFactory.newValue(12);

        /**
         * Definition of a known Feature.
         */
        public static final FeatureDefinition HOVER_BOARD_DEFINITION =
                FeatureDefinition.fromIdentifier("org.eclipse.ditto:hoverboard:1.0.0");

        /**
         * Properties of a known Feature.
         */
        public static final FeatureProperties HOVER_BOARD_PROPERTIES =
                ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("speed", 20)
                        .set("height_above_ground", "12")
                        .set("stability_factor", "2")
                        .build();

        /**
         * Desired properties of a known Feature.
         */
        public static final FeatureProperties HOVER_BOARD_DESIRED_PROPERTIES =
                ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("speed", 32)
                        .set("height_above_ground", "16")
                        .set("stability_factor", "10")
                        .build();

        /**
         * A known Feature which is required for relaxed locomotion.
         */
        public static final org.eclipse.ditto.things.model.Feature HOVER_BOARD =
                ThingsModelFactory.newFeatureBuilder()
                        .definition(HOVER_BOARD_DEFINITION)
                        .properties(HOVER_BOARD_PROPERTIES)
                        .desiredProperties(HOVER_BOARD_DESIRED_PROPERTIES)
                        .withId(HOVER_BOARD_ID)
                        .build();

        /**
         * Known features of a Thing.
         */
        public static final Features FEATURES =
                ThingsModelFactory.newFeaturesBuilder()
                        .set(FLUX_CAPACITOR)
                        .set(HOVER_BOARD)
                        .build();

        /**
         * A known {@code FeaturesNotAccessibleException}.
         */
        public static final FeaturesNotAccessibleException FEATURES_NOT_ACCESSIBLE_EXCEPTION =
                FeaturesNotAccessibleException.newBuilder(Thing.THING_ID).build();

        /**
         * A known {@code FeaturesNotModifiableException}.
         */
        public static final FeaturesNotModifiableException FEATURES_NOT_MODIFIABLE_EXCEPTION =
                FeaturesNotModifiableException.newBuilder(Thing.THING_ID).build();

        /**
         * A known {@code FeatureNotAccessibleException}.
         */
        public static final FeatureNotAccessibleException FEATURE_NOT_ACCESSIBLE_EXCEPTION =
                FeatureNotAccessibleException.newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID).build();

        /**
         * A known {@code FeatureNotModifiableException}.
         */
        public static final FeatureNotModifiableException FEATURE_NOT_MODIFIABLE_EXCEPTION =
                FeatureNotModifiableException.newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID).build();

        /**
         * A known {@code FeatureDefinitionNotAccessibleException}.
         */
        public static final FeatureDefinitionNotAccessibleException FEATURE_DEFINITION_NOT_ACCESSIBLE_EXCEPTION =
                FeatureDefinitionNotAccessibleException.newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID).build();

        /**
         * A known {@code FeatureDefinitionNotModifiableException}.
         */
        public static final FeatureDefinitionNotModifiableException FEATURE_DEFINITION_NOT_MODIFIABLE_EXCEPTION =
                FeatureDefinitionNotModifiableException.newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID).build();

        /**
         * A known {@code FeaturePropertiesNotAccessibleException}.
         */
        public static final FeaturePropertiesNotAccessibleException FEATURE_PROPERTIES_NOT_ACCESSIBLE_EXCEPTION =
                FeaturePropertiesNotAccessibleException.newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID).build();

        /**
         * A known {@code FeaturePropertiesNotModifiableException}.
         */
        public static final FeaturePropertiesNotModifiableException FEATURE_PROPERTIES_NOT_MODIFIABLE_EXCEPTION =
                FeaturePropertiesNotModifiableException.newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID).build();

        /**
         * A known {@code FeaturePropertyNotAccessibleException}.
         */
        public static final FeaturePropertyNotAccessibleException FEATURE_PROPERTY_NOT_ACCESSIBLE_EXCEPTION =
                FeaturePropertyNotAccessibleException
                        .newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTY_POINTER).build();

        /**
         * A known {@code FeaturePropertyNotModifiableException}.
         */
        public static final FeaturePropertyNotModifiableException FEATURE_PROPERTY_NOT_MODIFIABLE_EXCEPTION =
                FeaturePropertyNotModifiableException
                        .newBuilder(Thing.THING_ID, FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTY_POINTER).build();

        /**
         * A known {@code FeatureDesiredPropertiesNotAccessibleException}.
         */
        public static final FeatureDesiredPropertiesNotAccessibleException
                FEATURE_DESIRED_PROPERTIES_NOT_ACCESSIBLE_EXCEPTION =
                FeatureDesiredPropertiesNotAccessibleException.newBuilder(Thing.THING_ID, HOVER_BOARD_ID).build();

        /**
         * A known {@code FeatureDesiredPropertiesNotModifiableException}.
         */
        public static final FeatureDesiredPropertiesNotModifiableException
                FEATURE_DESIRED_PROPERTIES_NOT_MODIFIABLE_EXCEPTION =
                FeatureDesiredPropertiesNotModifiableException.newBuilder(Thing.THING_ID, HOVER_BOARD_ID).build();

        /**
         * A known {@code FeatureDesiredPropertyNotAccessibleException}.
         */
        public static final FeatureDesiredPropertyNotAccessibleException
                FEATURE_DESIRED_PROPERTY_NOT_ACCESSIBLE_EXCEPTION =
                FeatureDesiredPropertyNotAccessibleException
                        .newBuilder(Thing.THING_ID, HOVER_BOARD_ID, HOVER_BOARD_DESIRED_PROPERTY_POINTER).build();

        /**
         * A known {@code FeatureDesiredPropertyNotModifiableException}.
         */
        public static final FeatureDesiredPropertyNotModifiableException
                FEATURE_DESIRED_PROPERTY_NOT_MODIFIABLE_EXCEPTION =
                FeatureDesiredPropertyNotModifiableException
                        .newBuilder(Thing.THING_ID, HOVER_BOARD_ID, HOVER_BOARD_DESIRED_PROPERTY_POINTER).build();

        private Feature() {
            throw new AssertionError();
        }
    }

    public static class Pointer {

        public static final JsonPointer EMPTY_JSON_POINTER = JsonFactory.emptyPointer();
        public static final JsonPointer VALID_JSON_POINTER = JsonFactory.newPointer("properties/foo");
        public static final JsonPointer INVALID_JSON_POINTER = JsonFactory.newPointer("key1/äöü/foo");

    }

}
