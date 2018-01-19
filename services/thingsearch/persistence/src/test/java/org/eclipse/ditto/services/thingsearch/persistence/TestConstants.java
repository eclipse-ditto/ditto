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
package org.eclipse.ditto.services.thingsearch.persistence;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    public static String thingId(final String namespace, final String idWithoutNamespace) {
        return requireNonNull(namespace) + ":" + requireNonNull(idWithoutNamespace);
    }

    /**
     * Authorization-related test constants.
     */
    public static final class Authorization {

        /**
         * A known Authorization Subject for testing.
         */
        public static final AuthorizationSubject AUTH_SUBJECT_OLDMAN =
                AuthorizationModelFactory.newAuthSubject("JohnOldman");

        /**
         * Another known AuthorizationSubject for testing.
         */
        public static final AuthorizationSubject AUTH_SUBJECT_GRIMES =
                AuthorizationModelFactory.newAuthSubject("FrankGrimes");

        /**
         * An Authorization Context which contains all known Authorization Subjects.
         */
        public static final AuthorizationContext AUTH_CONTEXT =
                AuthorizationModelFactory.newAuthContext(AUTH_SUBJECT_OLDMAN, AUTH_SUBJECT_GRIMES);

        /**
         * The known ACL entry of John Oldman.
         */
        public static final AclEntry ACL_ENTRY_OLDMAN =
                ThingsModelFactory.newAclEntry(Authorization.AUTH_SUBJECT_OLDMAN, Permission.READ, Permission.WRITE,
                        Permission.ADMINISTRATE);

        /**
         * The known ACL entry of Frank Grimes.
         */
        public static final AclEntry ACL_ENTRY_GRIMES =
                ThingsModelFactory.newAclEntry(Authorization.AUTH_SUBJECT_GRIMES, Permission.READ);

        private Authorization() {
            throw new AssertionError();
        }
    }

    /**
     * Policy-related test constants.
     */
    public static final class Policy {

        /**
         * The known type.
         */
        public static final String TYPE = "things";

        /**
         * The known "read" permission.
         */
        public static final String PERMISSION_READ = "READ";

        /**
         * The known "write" permission.
         */
        public static final String PERMISSION_WRITE = "WRITE";

        /**
         * A known Subject Id subject.
         */
        public static final String SUBJECT_ID_SUBJECT = "LukeSkywalker";

        /**
         * A known Subject Id.
         */
        public static final SubjectId SUBJECT_ID =
                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ID_SUBJECT);

        /**
         * A known SubjectType.
         */
        public static final SubjectType SUBJECT_TYPE = SubjectType.newInstance("mySubjectType");

        /**
         * A known Subject.
         */
        public static final Subject SUBJECT = Subject.newInstance(SUBJECT_ID, SUBJECT_TYPE);

        /**
         * A known resource type.
         */
        public static final String RESOURCE_TYPE = "thing";

        /**
         * A known resource path.
         */
        public static final JsonPointer RESOURCE_PATH = JsonPointer.of("/foo/bar");

        /**
         * A known resource path char sequence.
         */
        public static final ResourceKey RESOURCE_KEY = ResourceKey.newInstance(RESOURCE_TYPE, RESOURCE_PATH);

        /**
         * All known permissions.
         */
        public static final Iterable<String> PERMISSIONS_ALL = Arrays.asList(PERMISSION_READ, PERMISSION_WRITE);

        private Policy() {
            throw new AssertionError();
        }
    }

    /**
     * Feature-related test constants.
     */
    public static final class Feature {

        public static final String FLUX_CAPACITOR_ID = "FluxCapacitor";

        public static final FeatureProperties FLUX_CAPACITOR_PROPERTIES =
                ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("target_year_1", 1955)
                        .set("target_year_2", 2015)
                        .set("target_year_3", 1885)
                        .build();

        public static final org.eclipse.ditto.model.things.Feature FLUX_CAPACITOR =
                ThingsModelFactory.newFeatureBuilder()
                        .properties(FLUX_CAPACITOR_PROPERTIES)
                        .withId(FLUX_CAPACITOR_ID)
                        .build();


        public static final Features FEATURES = ThingsModelFactory.newFeatures(FLUX_CAPACITOR);

        private Feature() {
            throw new AssertionError();
        }
    }

    /**
     * Thing-related test constants.
     */
    public static final class Thing {

        public static final String NAMESPACE = "example.com.things";

        public static final String THING_ID = NAMESPACE + ":testThing";

        public static final String POLICY_ID = NAMESPACE + ":testPolicy";

        public static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;

        public static final AccessControlList ACL =
                ThingsModelFactory.newAcl(Authorization.ACL_ENTRY_OLDMAN, Authorization.ACL_ENTRY_GRIMES);

        private static final String MANUFACTURER_ATTRIBUTE_KEY = "manufacturer";
        public static final JsonPointer MANUFACTURER_POINTER = JsonFactory.newPointer("attributes/" +
                MANUFACTURER_ATTRIBUTE_KEY);

        public static final JsonObject LOCATION_ATTRIBUTE = JsonFactory.newObjectBuilder()
                .set("latitude", 44.673856)
                .set("longitude", 8.261719)
                .build();

        public static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
                .set("location", LOCATION_ATTRIBUTE)
                .set(MANUFACTURER_ATTRIBUTE_KEY, "Bosch")
                .build();

        public static final long REVISION_NUMBER = 0;

        public static final ThingRevision REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);

        public static final org.eclipse.ditto.model.things.Thing THING_V1 = ThingsModelFactory.newThingBuilder()
                .setAttributes(ATTRIBUTES)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setPermissions(ACL)
                .setId(THING_ID)
                .build();

        /**
         * A known V2 Thing for testing.
         */
        public static final org.eclipse.ditto.model.things.Thing THING = ThingsModelFactory.newThingBuilder()
                .setPolicyId(POLICY_ID)
                .setAttributes(ATTRIBUTES)
                .setFeatures(Feature.FEATURES)
                .setLifecycle(LIFECYCLE)
                .setId(THING_ID)
                .build();

        private Thing() {
            throw new AssertionError();
        }
    }

    /**
     * ThingEvent-related test constants.
     */
    public static final class ThingEvent {

        public static final long REVISION = 42L;
        public static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
        public static final JsonPointer ATTRIBUTE_KEY = JsonPointer.of("location");
        public static final JsonValue ATTRIBUTE_VALUE = Thing.LOCATION_ATTRIBUTE;
        public static final JsonPointer FEATURE_KEY = JsonPointer.of("target_year_1");
        public static final JsonValue FEATURE_VALUE = JsonValue.of(1955);

        public static final org.eclipse.ditto.signals.events.things.AclEntryCreated ACL_ENTRY_CREATED =
                AclEntryCreated.of(Thing.THING_ID, Authorization.ACL_ENTRY_GRIMES, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AclEntryModified ACL_ENTRY_MODIFIED =
                AclEntryModified.of(Thing.THING_ID, Authorization.ACL_ENTRY_GRIMES, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AclEntryDeleted ACL_ENTRY_DELETED =
                AclEntryDeleted.of(Thing.THING_ID, Authorization.AUTH_SUBJECT_GRIMES, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AclModified ACL_MODIFIED =
                AclModified.of(Thing.THING_ID, Thing.ACL, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AttributeCreated ATTRIBUTE_CREATED =
                AttributeCreated.of(Thing.THING_ID, ATTRIBUTE_KEY, ATTRIBUTE_VALUE, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AttributeModified ATTRIBUTE_MODIFIED =
                AttributeModified.of(Thing.THING_ID, ATTRIBUTE_KEY, ATTRIBUTE_VALUE, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AttributeDeleted ATTRIBUTE_DELETED =
                AttributeDeleted.of(Thing.THING_ID, ATTRIBUTE_KEY, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AttributesCreated ATTRIBUTES_CREATED =
                AttributesCreated.of(Thing.THING_ID, Thing.ATTRIBUTES, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AttributesModified ATTRIBUTES_MODIFIED =
                AttributesModified.of(Thing.THING_ID, Thing.ATTRIBUTES, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.AttributesDeleted ATTRIBUTES_DELETED =
                AttributesDeleted.of(Thing.THING_ID, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeatureCreated FEATURE_CREATED =
                FeatureCreated.of(Thing.THING_ID, Feature.FLUX_CAPACITOR, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeatureModified FEATURE_MODIFIED =
                FeatureModified.of(Thing.THING_ID, Feature.FLUX_CAPACITOR, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeatureDeleted FEATURE_DELETED =
                FeatureDeleted.of(Thing.THING_ID, Feature.FLUX_CAPACITOR.getId(), REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturesCreated FEATURES_CREATED =
                FeaturesCreated.of(Thing.THING_ID, Feature.FEATURES, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturesModified FEATURES_MODIFIED =
                FeaturesModified.of(Thing.THING_ID, Feature.FEATURES, REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturesDeleted FEATURES_DELETED =
                FeaturesDeleted.of(Thing.THING_ID, REVISION, DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturePropertyCreated
                FEATURE_PROPERTY_CREATED =
                FeaturePropertyCreated.of(Thing.THING_ID,
                        Feature.FLUX_CAPACITOR_ID,
                        FEATURE_KEY,
                        FEATURE_VALUE,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturePropertyModified
                FEATURE_PROPERTY_MODIFIED =
                FeaturePropertyModified.of(Thing.THING_ID,
                        Feature.FLUX_CAPACITOR_ID,
                        FEATURE_KEY,
                        FEATURE_VALUE,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted
                FEATURE_PROPERTY_DELETED =
                FeaturePropertyDeleted.of(Thing.THING_ID,
                        Feature.FLUX_CAPACITOR_ID,
                        FEATURE_KEY,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated
                FEATURE_PROPERTIES_CREATED =
                FeaturePropertiesCreated.of(Thing.THING_ID,
                        Feature.FLUX_CAPACITOR_ID,
                        Feature.FLUX_CAPACITOR_PROPERTIES,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturePropertiesModified
                FEATURE_PROPERTIES_MODIFIED =
                FeaturePropertiesModified.of(Thing.THING_ID,
                        Feature.FLUX_CAPACITOR_ID,
                        Feature.FLUX_CAPACITOR_PROPERTIES,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted
                FEATURE_PROPERTIES_DELETED =
                FeaturePropertiesDeleted.of(Thing.THING_ID,
                        Feature.FLUX_CAPACITOR_ID,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.ThingCreated
                THING_CREATED_V1 =
                ThingCreated.of(Thing.THING_V1,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.ThingModified
                THING_MODIFIED_V1 =
                ThingModified.of(Thing.THING_V1,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.ThingDeleted
                THING_DELETED_V1 =
                ThingDeleted.of(Thing.THING_ID,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.ThingCreated
                THING_CREATED =
                ThingCreated.of(Thing.THING,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.ThingModified
                THING_MODIFIED =
                ThingModified.of(Thing.THING,
                        REVISION,
                        DITTO_HEADERS);

        public static final org.eclipse.ditto.signals.events.things.ThingDeleted
                THING_DELETED =
                ThingDeleted.of(Thing.THING_ID,
                        REVISION,
                        DITTO_HEADERS);

    }


}
