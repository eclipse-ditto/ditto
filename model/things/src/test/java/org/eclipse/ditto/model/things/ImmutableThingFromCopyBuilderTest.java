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
package org.eclipse.ditto.model.things;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;

import java.util.Collections;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableThingFromCopyBuilder}.
 */
public final class ImmutableThingFromCopyBuilderTest {

    private static final JsonPointer ATTRIBUTE_PATH = JsonFactory.newPointer("location/longitude");
    private static final JsonValue ATTRIBUTE_VALUE = JsonFactory.newValue(42);
    private static final JsonPointer PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue(1337);

    private ImmutableThingFromCopyBuilder underTestV1 = null;
    private ImmutableThingFromCopyBuilder underTestV2 = null;

    @Before
    public void setUp() {
        underTestV1 = ImmutableThingFromCopyBuilder.of(TestConstants.Thing.THING_V1);
        underTestV2 = ImmutableThingFromCopyBuilder.of(TestConstants.Thing.THING_V2);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceOfNullThing() {
        ImmutableThingFromCopyBuilder.of((Thing) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceOfNullJsonObject() {
        ImmutableThingFromCopyBuilder.of((JsonObject) null);
    }

    @Test
    public void builderOfThingIsCorrectlyInitialised() {
        final Thing thing = underTestV1.build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V1);
    }

    @Test
    public void builderOfThingIsCorrectlyInitialisedV2() {
        final Thing thing = underTestV2.build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V2);
    }

    @Test
    public void builderOfJsonObjectIsCorrectlyInitialised() {
        underTestV1 = ImmutableThingFromCopyBuilder.of(
                TestConstants.Thing.THING_V1.toJson(JsonSchemaVersion.V_1, FieldType.regularOrSpecial()));
        final Thing thing = underTestV1.build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V1);
    }

    @Test
    public void builderOfJsonObjectIsCorrectlyInitialisedV2() {
        underTestV2 = ImmutableThingFromCopyBuilder.of(
                TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial()));
        final Thing thing = underTestV2.build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V2);
    }

    @Test(expected = JsonParseException.class)
    public void builderOfJsonObjectThrowsCorrectExceptionForDateTimeParseException() {
        underTestV1 =
                ImmutableThingFromCopyBuilder.of(
                        TestConstants.Thing.THING_V1.toJson(JsonSchemaVersion.V_1, FieldType.regularOrSpecial())
                        .toBuilder()
                        .set(Thing.JsonFields.MODIFIED, "10.10.2016 13:37")
                        .build());

        underTestV1.build();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullAttributes() {
        underTestV1.setAttributes((Attributes) null);
    }

    @Test
    public void setAttributes() {
        underTestV1.setAttributes(TestConstants.Thing.ATTRIBUTES);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributesFromNullJsonObject() {
        underTestV1.setAttributes((JsonObject) null);
    }

    @Test
    public void setAttributesFromJsonObject() {
        final JsonObject attributesJsonObject = TestConstants.Thing.ATTRIBUTES.toJson();
        underTestV1.setAttributes(attributesJsonObject);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }

    @Test
    public void setAttributesFromSemanticNullJsonObject() {
        underTestV1.setAttributes(JsonFactory.nullObject());
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test
    public void setAttributesFromSemanticNullJsonString() {
        underTestV1.setAttributes("null");
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test
    public void setNullAttributes() {
        underTestV1.setNullAttributes();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttributes(ThingsModelFactory.nullAttributes());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetAttributesFromNullJsonString() {
        underTestV1.setAttributes((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetAttributesFromEmptyJsonString() {
        underTestV1.setAttributes("");
    }

    @Test
    public void setAttributesFromJsonString() {
        final String attributesJsonString = TestConstants.Thing.ATTRIBUTES.toJsonString();
        underTestV1.setAttributes(attributesJsonString);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }

    @Test
    public void removeAllAttributes() {
        underTestV1.setAttributes(TestConstants.Thing.ATTRIBUTES);
        underTestV1.removeAllAttributes();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoAttributes();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributeWithNullPath() {
        underTestV1.setAttribute(null, ATTRIBUTE_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetAttributeWithNullValue() {
        underTestV1.setAttribute(ATTRIBUTE_PATH, null);
    }

    @Test
    public void setAttribute() {
        underTestV1.setAttribute(ATTRIBUTE_PATH, ATTRIBUTE_VALUE);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAttribute(ATTRIBUTE_PATH, ATTRIBUTE_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveAttributeWithNullPath() {
        underTestV1.setAttributes(TestConstants.Thing.ATTRIBUTES);
        underTestV1.removeAttribute(null);
    }

    @Test
    public void removeAttribute() {
        underTestV1.setAttributes(TestConstants.Thing.ATTRIBUTES);
        underTestV1.removeAttribute(ATTRIBUTE_PATH);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNotAttribute(ATTRIBUTE_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetNullFeature() {
        underTestV1.setFeature((Feature) null);
    }

    @Test
    public void setFeature() {
        underTestV1.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setNullFeature() {
        final String nullFeatureId = "schroedinger";
        underTestV1.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV1.setFeature(ThingsModelFactory.nullFeature(nullFeatureId));
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasFeature(TestConstants.Feature.FLUX_CAPACITOR)
                .hasFeatureWithId(nullFeatureId);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeatureWithNullId() {
        underTestV1.setFeature((String) null);
    }

    @Test
    public void setFeatureById() {
        underTestV1.setFeature(FLUX_CAPACITOR_ID);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatureWithId(FLUX_CAPACITOR_ID);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeatureWithPropertiesWithNullId() {
        underTestV1.setFeature(null, FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void setFeatureWithProperties() {
        underTestV1.setFeature(FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION,
                FLUX_CAPACITOR_PROPERTIES);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void tryToRemoveFeatureWithNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTestV1.removeFeature(null))
                .withMessage("The %s must not be null!", "identifier of the feature to be removed")
                .withNoCause();
    }

    @Test
    public void removeFeature() {
        underTestV1.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV1.removeFeature(FLUX_CAPACITOR_ID);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.emptyFeatures());
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFetFeaturePropertyForNullFeatureId() {
        underTestV1.setFeatureProperty(null, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturePropertyWithNullPath() {
        underTestV1.setFeatureProperty(FLUX_CAPACITOR_ID, null, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturePropertyWithNullValue() {
        underTestV1.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, null);
    }

    @Test
    public void setFeaturePropertyOnEmptyBuilder() {
        underTestV1.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyUsingPositivePredicateOnEmptyBuilder() {
        underTestV1.setFeatureProperty(features -> true, FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyUsingNegativePredicateOnEmptyBuilder() {
        underTestV1.setFeatureProperty(features -> false, FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setFeaturePropertyOnBuilderWithFeatures() {
        underTestV1.setFeatures(FEATURES);
        underTestV1.setFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH, PROPERTY_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeaturePropertyForNullFeatureId() {
        underTestV1.removeFeatureProperty(null, PROPERTY_PATH);
    }


    @Test(expected = NullPointerException.class)
    public void tryToRemoveFeaturePropertyWithNullPath() {
        underTestV1.removeFeatureProperty(FLUX_CAPACITOR_ID, null);
    }

    @Test
    public void removeFeatureProperty() {
        underTestV1.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV1.removeFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNotFeatureProperty(FLUX_CAPACITOR_ID, PROPERTY_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturesWithNullIterable() {
        underTestV1.setFeatures((Iterable<Feature>) null);
    }

    @Test
    public void setFeatures() {
        underTestV1.setFeatures(FEATURES);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(FEATURES);
    }

    @Test
    public void setFeaturesWithPositivePredicate() {
        underTestV1.removeAllFeatures();
        underTestV1.setFeatures(features -> true, FEATURES);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(FEATURES);
    }

    @Test
    public void setFeaturesWithNegativePredicate() {
        underTestV1.removeAllFeatures();
        underTestV1.setFeatures(features -> false, FEATURES);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetFeaturesFromNullJsonObject() {
        underTestV1.setFeatures((JsonObject) null);
    }

    @Test
    public void setFeaturesFromSemanticNullJsonObject() {
        underTestV1.setFeatures(JsonFactory.nullObject());
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.nullFeatures());
    }

    @Test
    public void setNullFeatures() {
        underTestV1.setNullFeatures();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(ThingsModelFactory.nullFeatures());
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetFeaturesFromNullJsonString() {
        underTestV1.setFeatures((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetFeaturesFromEmptyJsonString() {
        underTestV1.setFeatures("");
    }

    @Test
    public void setFeaturesFromJsonString() {
        final String featuresJsonString = FEATURES.toJsonString();
        underTestV1.setFeatures(featuresJsonString);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(FEATURES);
    }

    @Test
    public void setFeaturesFromSemanticNullJsonString() {
        final Features nullFeatures = ThingsModelFactory.nullFeatures();
        final String featuresJsonString = nullFeatures.toJsonString();
        underTestV1.setFeatures(featuresJsonString);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeatures(nullFeatures);
    }

    @Test
    public void removeAllFeatures() {
        underTestV1.setFeatures(FEATURES);
        underTestV1.removeAllFeatures();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void removeAllFeaturesWhenFeaturesAreEmpty() {
        final Features emptyFeatures = ThingsModelFactory.emptyFeatures();
        underTestV1.setFeatures(emptyFeatures);
        underTestV1.removeAllFeatures();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void remove() {
        underTestV1.setFeature(TestConstants.Feature.FLUX_CAPACITOR);
        underTestV1.removeAllFeatures();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void setLifecycle() {
        underTestV1.setLifecycle(TestConstants.Thing.LIFECYCLE);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasLifecycle(TestConstants.Thing.LIFECYCLE);
    }

    @Test
    public void setNullLifecycle() {
        underTestV1.setLifecycle(TestConstants.Thing.LIFECYCLE);
        underTestV1.setLifecycle(null);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoLifecycle();
    }

    @Test
    public void setRevision() {
        underTestV1.setRevision(TestConstants.Thing.REVISION);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasRevision(TestConstants.Thing.REVISION);
    }

    @Test
    public void setNullRevision() {
        underTestV1.setRevision(TestConstants.Thing.REVISION);
        underTestV1.setRevision(null);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoRevision();
    }

    @Test
    public void setRevisionByNumber() {
        underTestV1.setRevision(TestConstants.Thing.REVISION_NUMBER);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasRevision(TestConstants.Thing.REVISION);
    }

    @Test
    public void setModified() {
        underTestV1.setModified(TestConstants.Thing.MODIFIED);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasModified(TestConstants.Thing.MODIFIED);
    }

    @Test
    public void setNullModified() {
        underTestV1.setModified(TestConstants.Thing.MODIFIED);
        underTestV1.setModified(null);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoModified();
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetPermissionsWithNullJsonObject() {
        underTestV1.setPermissions((JsonObject) null);
    }

    @Test
    public void setPermissionsWithSemanticNullJsonObject() {
        underTestV1.setPermissions(JsonFactory.nullObject());
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAcl(TestConstants.Thing.ACL);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetPermissionsWithNullJsonString() {
        underTestV1.setPermissions((String) null);
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetPermissionsWithEmptyJsonString() {
        underTestV1.setPermissions("");
    }

    @Test(expected = DittoJsonException.class)
    public void tryToSetPermissionsWithArrayJsonString() {
        underTestV1.setPermissions("[\"a\",\"b\"]");
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetSinglePermissionsWithNullAuthorizationSubject() {
        underTestV1.setPermissions(null, Permission.READ, Permission.WRITE);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetSinglePermissionsWithNullPermission() {
        underTestV1.setPermissions(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, null);
    }

    @Test
    public void setSinglePermissions() {
        underTestV1.setPermissions(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ, Permission.WRITE);
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ, Permission.WRITE);
    }

    @Test
    public void setSinglePermissionsWithTruePredicate() {
        final Predicate<AccessControlList> p =
                acl -> !acl.hasPermission(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.ADMINISTRATE);

        underTestV1.setPermissions(p, TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ,
                Permission.ADMINISTRATE);
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ, Permission.ADMINISTRATE);
    }

    @Test
    public void doNotSetSinglePermissionsWithFalsePredicate() {
        final Predicate<AccessControlList> p =
                acl -> acl.hasPermission(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.ADMINISTRATE);

        underTestV1.setPermissions(p, TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.READ);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetMultiplePermissionsWithNullAuthorizationSubject() {
        underTestV1.setPermissions(null, ThingsModelFactory.allPermissions());
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetMultiplePermissionsWithNullPermissions() {
        underTestV1.setPermissions(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, null);
    }

    @Test
    public void setMultiplePermissions() {
        underTestV1.setPermissions(TestConstants.Authorization.AUTH_SUBJECT_GRIMES,
                ThingsModelFactory.allPermissions());
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, ThingsModelFactory.allPermissions());
    }

    @Test
    public void setMultiplePermissionsWithTruePredicate() {
        final Predicate<AccessControlList> p = acl -> true;

        underTestV1.setPermissions(p, TestConstants.Authorization.AUTH_SUBJECT_GRIMES,
                ThingsModelFactory.allPermissions());
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, ThingsModelFactory.allPermissions());
    }

    @Test
    public void doNotSetMultiplePermissionsWithFalsePredicate() {
        final Predicate<AccessControlList> p = acl -> false;

        underTestV1.setPermissions(TestConstants.Thing.ACL);
        underTestV1.setPermissions(p, TestConstants.Authorization.AUTH_SUBJECT_GRIMES,
                ThingsModelFactory.allPermissions());
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetPermissionsWithNullAclEntry() {
        underTestV1.setPermissions((AclEntry) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetPermissionsWithNullFurtherAclEntries() {
        underTestV1.setPermissions(TestConstants.Authorization.ACL_ENTRY_GRIMES, null);
    }

    @Test
    public void setPermissionsBySingleAclEntries() {
        underTestV1.setPermissions(TestConstants.Authorization.ACL_ENTRY_GRIMES,
                TestConstants.Authorization.ACL_ENTRY_OLDMAN);
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES)
                .hasAclEntry(TestConstants.Authorization.ACL_ENTRY_OLDMAN);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetPermissionsWithNullAclEntryIterable() {
        underTestV1.setPermissions((Iterable<AclEntry>) null);
    }

    @Test
    public void setPermissionsWithEmptyAclEntryIterable() {
        underTestV1.setPermissions(Collections.emptySet());
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAcl(TestConstants.Thing.ACL);
    }

    @Test
    public void setPermissionsFromExistingAcl() {
        underTestV1.setPermissions(TestConstants.Thing.ACL);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAcl(TestConstants.Thing.ACL);
    }

    @Test
    public void setPermissionsFromExistingAclWithTruePredicate() {
        final Predicate<AccessControlList> p = acl -> true;

        underTestV1.removeAllPermissions();
        underTestV1.setPermissions(p, TestConstants.Thing.ACL);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAcl(TestConstants.Thing.ACL);
    }

    @Test
    public void doNotSetPermissionsFromExistingAclWithFalsePredicate() {
        final Predicate<AccessControlList> p = acl -> false;

        underTestV1.removeAllPermissions();
        underTestV1.setPermissions(p, TestConstants.Thing.ACL);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoAcl();
    }

    @Test
    public void setPermissionsWithJsonObjectWithTruePredicate() {
        final Predicate<AccessControlList> p = acl -> true;

        underTestV1.removeAllPermissions();
        underTestV1.setPermissions(p, TestConstants.Thing.ACL);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAcl(TestConstants.Thing.ACL);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemovePermissionsOfNullAuthorizationSubject() {
        underTestV1.removePermissionsOf(null);
    }

    @Test
    public void removePermissionsOfAuthorizationSubjectFromExistingAcl() {
        underTestV1.setPermissions(TestConstants.Thing.ACL);
        underTestV1.removePermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_OLDMAN);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasAcl(
                TestConstants.Thing.ACL.removeAllPermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_OLDMAN));
    }

    @Test
    public void removeAllPermissions() {
        underTestV1.setPermissions(TestConstants.Thing.ACL);
        underTestV1.removeAllPermissions();
        final Thing thing = underTestV1.build();

        assertThat(thing).hasNoAcl();
    }

    @Test
    public void setPolicyIdV2() {
        underTestV2.setPolicyId(TestConstants.Thing.POLICY_ID);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasPolicyId(TestConstants.Thing.POLICY_ID);
    }

    @Test
    public void removePolicyId() {
        underTestV2.setPolicyId(TestConstants.Thing.POLICY_ID);
        underTestV2.removePolicyId();
        final Thing thing = underTestV2.build();

        assertThat(thing.getPolicyId()).isEmpty();
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetEmptyThingId() {
        underTestV1.setId("");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithMissingNamespace() {
        underTestV1.setId("foobar2000");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithInvalidCharactersInNamespace() {
        underTestV1.setId("foo-bar:foobar2000");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithInvalidCharactersInNamespace2() {
        underTestV1.setId("foo.bar%bum:foobar2000");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithNamespaceStartingWithPeriod() {
        underTestV1.setId(".namespace:foobar2000");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithNamespaceEndingWithPeriod() {
        underTestV1.setId("namespace.:foobar2000");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithTwoSubsequentPeriodsInNamespace() {
        underTestV1.setId("namespace..invalid:foobar2000");
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToSetIdWithNamespaceWithNumberAfterPeriod() {
        underTestV1.setId("namespace.42:foobar2000");
    }

    @Test
    public void setIdWithEmptyNamespace() {
        assertSetIdWithValidNamespace("");
    }

    @Test
    public void setIdWithTopLevelNamespace() {
        assertSetIdWithValidNamespace("ad");
    }

    @Test
    public void setIdWithTwoLevelNamespace() {
        assertSetIdWithValidNamespace("foo.a42");
    }

    @Test
    public void setIdWithNamespaceContainingNumber() {
        assertSetIdWithValidNamespace("da23");
    }

    @Test
    public void setGeneratedId() {
        underTestV1.setGeneratedId();
        final Thing thing = underTestV1.build();

        assertThat(thing.getId()).isPresent();
    }

    @Test
    public void tryToSetNullFeatureDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTestV1.setFeatureDefinition(FLUX_CAPACITOR_ID, null))
                .withMessage("The %s must not be null!", "Feature Definition to be set")
                .withNoCause();
    }

    @Test
    public void setFeatureDefinitionCreatesFeatureIfNecessary() {
        final FeatureDefinition definition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final Feature expected = ThingsModelFactory.newFeature(FLUX_CAPACITOR_ID, definition);
        underTestV1.removeAllFeatures();
        underTestV1.setFeatureDefinition(FLUX_CAPACITOR_ID, definition);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeature(expected);
    }

    @Test
    public void setFeatureDefinitionExtendsAlreadySetFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final FeatureDefinition featureDefinition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final FeatureProperties featureProperties = FLUX_CAPACITOR_PROPERTIES;
        final Feature featureWithoutDefinition = ThingsModelFactory.newFeature(featureId, featureProperties);
        final Feature expected = ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties);

        underTestV2.setFeature(featureWithoutDefinition);
        underTestV2.setFeatureDefinition(featureId, featureDefinition);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasFeature(expected);
    }

    @Test
    public void removeFeatureDefinitionFromUnknownFeatureIdDoesNothing() {
        underTestV2.removeAllFeatures();
        underTestV2.removeFeatureDefinition(FLUX_CAPACITOR_ID);
        final Thing thing = underTestV2.build();

        assertThat(thing).hasNoFeatures();
    }

    @Test
    public void removeFeatureDefinitionWorksAsExpected() {
        final String featureId = FLUX_CAPACITOR_ID;
        final FeatureDefinition featureDefinition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final FeatureProperties featureProperties = FLUX_CAPACITOR_PROPERTIES;
        final Feature expected = ThingsModelFactory.newFeature(featureId, featureProperties);

        underTestV1.setFeature(ThingsModelFactory.newFeature(featureId, featureDefinition, featureProperties));
        underTestV1.removeFeatureDefinition(featureId);
        final Thing thing = underTestV1.build();

        assertThat(thing).hasFeature(expected);
    }

    private void assertSetIdWithValidNamespace(final String namespace) {
        final String thingId = namespace + ":" + "foobar2000";
        underTestV1.setId(thingId);
        final Thing thing = underTestV1.build();

        assertThat(thing)
                .hasId(thingId)
                .hasNamespace(namespace);
    }

}
