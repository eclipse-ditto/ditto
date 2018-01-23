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

import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableThing}.
 */
public final class ImmutableThingTest {

    private static final Features EMPTY_FEATURES = ThingsModelFactory.emptyFeatures();

    private static final Thing
            KNOWN_THING_V1 = ImmutableThing.of(
            TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
            TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
            TestConstants.Thing.MODIFIED);
    private static final JsonPointer KNOWN_FEATURE_PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue KNOWN_FEATURE_PROPERTY_VALUE = JsonFactory.newValue(1955);

    private static final Thing
            KNOWN_THING_V2 = ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.POLICY_ID,
            TestConstants.Thing.ATTRIBUTES, TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE,
            TestConstants.Thing.REVISION,
            TestConstants.Thing.MODIFIED);


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableThing.class)
                .usingGetClass()
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableThing.class,
                areImmutable(),
                provided(Attributes.class, Features.class, JsonObject.class, AccessControlList.class,
                        ThingRevision.class)
                        .areAlsoImmutable(),
                assumingFields("policyEntries").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }


    @Test
    public void createThingWithoutId() {
        final Thing thing = ImmutableThing.of(null, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                TestConstants.Thing.MODIFIED);

        assertThat(thing) //
                .hasNoId() //
                .hasAcl(TestConstants.Thing.ACL) //
                .hasAttributes(TestConstants.Thing.ATTRIBUTES) //
                .hasFeatures(TestConstants.Feature.FEATURES) //
                .hasLifecycle(TestConstants.Thing.LIFECYCLE) //
                .hasRevision(TestConstants.Thing.REVISION) //
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutACL() {
        final Thing thing =
                ImmutableThing.of(TestConstants.Thing.THING_ID, (AccessControlList) null,
                        TestConstants.Thing.ATTRIBUTES, TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE,
                        TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasNoAcl()
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION)
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutPolicyId() {
        final Thing thing =
                ImmutableThing.of(TestConstants.Thing.THING_ID, (String) null, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasNoPolicyId()
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION)
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutAttributes() {
        final Thing thing = ImmutableThing.of(
                TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, null, TestConstants.Feature.FEATURES,
                TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasNoAttributes()
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION)
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutFeatures() {
        final Thing thing = ImmutableThing.of(
                TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES, null,
                TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasNoFeatures()
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION)
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutLifecycle() {
        final Thing thing = ImmutableThing.of(
                TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                TestConstants.Feature.FEATURES, null, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasNoLifecycle()
                .hasRevision(TestConstants.Thing.REVISION)
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutRevision() {
        final Thing thing = ImmutableThing.of(
                TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, null, TestConstants.Thing.MODIFIED);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasNoRevision()
                .hasModified(TestConstants.Thing.MODIFIED);
    }


    @Test
    public void createThingWithoutmodified() {
        final Thing thing = ImmutableThing.of(
                TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, null);

        assertThat(thing)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION)
                .hasNoModified();
    }


    @Test
    public void setAclWorksAsExpected() {
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(TestConstants.Thing.ACL)
                .set(ThingsModelFactory.newAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES,
                        Permission.WRITE))
                .build();

        final Thing changedThing = KNOWN_THING_V1.setAccessControlList(newAcl);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setAclEntryWorksAsExpected() {
        final AclEntry newAclEntry =
                ThingsModelFactory.newAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.WRITE);
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(TestConstants.Thing.ACL)
                .set(newAclEntry)
                .build();

        final Thing changedThing = KNOWN_THING_V1.setAclEntry(newAclEntry);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeAllPermissionsWorksAsExpected() {
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(TestConstants.Thing.ACL)
                .remove(TestConstants.Authorization.AUTH_SUBJECT_GRIMES)
                .build();

        final Thing changedThing =
                KNOWN_THING_V1.removeAllPermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_GRIMES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setPolicyIdWorksAsExpected() {
        final String newPolicyId = "foo:new";

        final Thing changedThing = KNOWN_THING_V2.setPolicyId(newPolicyId);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(TestConstants.Thing.THING_ID)
                .hasPolicyId(newPolicyId)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.setFeatures(EMPTY_FEATURES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeFeatures();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasNoFeatures()
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setFeaturePropertiesWorksAsExpected() {
        final FeatureProperties newFeatureProperties =
                ThingsModelFactory.newFeaturePropertiesBuilder(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES)
                        .set("target_year_4", 1337)
                        .build();

        final Thing changedThing =
                KNOWN_THING_V1.setFeatureProperties(TestConstants.Feature.FLUX_CAPACITOR_ID, newFeatureProperties);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatureProperties(TestConstants.Feature.FLUX_CAPACITOR_ID, newFeatureProperties)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeFeaturePropertiesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeFeatureProperties(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .featureHasNoProperties(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setLifecycleWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.setLifecycle(ThingLifecycle.DELETED);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(ThingLifecycle.DELETED)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setAttributesWorksAsExpected() {
        final Attributes newAttributes = ThingsModelFactory.newAttributesBuilder()
                .set("manufacturer", "Bosch SI")
                .build();

        final Thing changedThing = KNOWN_THING_V1.setAttributes(newAttributes);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(newAttributes)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");
        final JsonValue newAttributeValue = JsonFactory.newValue("Bosch SI");

        final Thing changedThing = KNOWN_THING_V1.setAttribute(attributePath, newAttributeValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttribute(attributePath, newAttributeValue)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeAttributesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeAttributes();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasNoAttributes()
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");

        final Thing changedThing = KNOWN_THING_V1.removeAttribute(attributePath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasNotAttribute(attributePath)
                .hasFeatures(TestConstants.Feature.FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setFeatureWorksAsExpected() {
        final String newFeatureId = "lamp";
        final Feature newFeature = ThingsModelFactory.newFeature(newFeatureId);

        final Features newFeatures = ThingsModelFactory.newFeaturesBuilder(TestConstants.Feature.FEATURES)
                .set(newFeature)
                .build();

        final Thing changedThing = KNOWN_THING_V1.setFeature(newFeature);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(newFeatures)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeFeatureWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeFeature(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void setFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_4");
        final JsonValue newPropertyValue = JsonFactory.newValue(1337);

        final Thing changedThing =
                KNOWN_THING_V1.setFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, propertyPath,
                        newPropertyValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, propertyPath, newPropertyValue)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void removeFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_2");

        final Thing changedThing =
                KNOWN_THING_V1.removeFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, propertyPath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(TestConstants.Thing.THING_ID)
                .hasAcl(TestConstants.Thing.ACL)
                .hasAttributes(TestConstants.Thing.ATTRIBUTES)
                .hasNotFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, propertyPath)
                .hasLifecycle(TestConstants.Thing.LIFECYCLE)
                .hasRevision(TestConstants.Thing.REVISION);
    }


    @Test
    public void tryToCreateThingWithValidThingIdNamespace() {
        ImmutableThing.of("foo.bar:foobar2000", TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES, EMPTY_FEATURES,
                TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);
    }


    @Test
    public void tryToCreateThingWithValidThingIdNamespace2() {
        ImmutableThing.of("foo.a42:foobar2000", TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES, EMPTY_FEATURES,
                TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);
    }


    @Test
    public void tryToCreateThingWithValidThingIdNamespace3() {
        ImmutableThing.of("ad:foobar2000", TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES, EMPTY_FEATURES,
                TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);
    }


    @Test
    public void tryToCreateThingWithValidThingIdNamespace4() {
        ImmutableThing.of("da23:foobar2000", TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES, EMPTY_FEATURES,
                TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION, TestConstants.Thing.MODIFIED);
    }


    @Test
    public void setAclEntryToThingWithoutAcl() {
        final Thing withoutAcl =
                ImmutableThing.of(TestConstants.Thing.THING_ID, (AccessControlList) null,
                        TestConstants.Thing.ATTRIBUTES, TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE,
                        TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withAcl = withoutAcl.setAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES);

        assertThat(withAcl).hasAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES);
    }


    @Test
    public void setSameAclEntryAgain() {
        final Thing withAcl = ImmutableThing.of(
                TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                TestConstants.Thing.MODIFIED);

        assertThat(withAcl.setAclEntry(TestConstants.Authorization.ACL_ENTRY_OLDMAN)).isSameAs(withAcl);
    }


    @Test
    public void removeAllPermissionsOfAuthSubjectFromThingWithoutAcl() {
        final Thing withoutAcl =
                ImmutableThing.of(TestConstants.Thing.THING_ID, (AccessControlList) null,
                        TestConstants.Thing.ATTRIBUTES, TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE,
                        TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withoutPermissionsForGrimes = withoutAcl.removeAllPermissionsOf(
                TestConstants.Authorization.AUTH_SUBJECT_GRIMES);

        assertThat(withoutPermissionsForGrimes).isSameAs(withoutAcl);
    }


    @Test
    public void removeAllPermissionsOfAuthSubjectWhichIsNotInAcl() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(TestConstants.Thing.THING_ID)
                .setAttributes(TestConstants.Thing.ATTRIBUTES)
                .setFeatures(TestConstants.Feature.FEATURES)
                .setPermissions(TestConstants.Authorization.ACL_ENTRY_OLDMAN)
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setRevision(TestConstants.Thing.REVISION)
                .build();

        assertThat(thing.removeAllPermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_GRIMES)).isSameAs(thing);
    }


    @Test
    public void setAttributeToThingWithoutAttributes() {
        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(42.0D);

        final Thing withoutAttributes = ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, null,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                TestConstants.Thing.MODIFIED);
        final Thing withAttribute = withoutAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).hasAttribute(latitudePath, latitudeValue);
    }


    @Test
    public void removeAttributeFromThingWithoutAttributes() {
        final Thing withoutAttributes = ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, null,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                TestConstants.Thing.MODIFIED);
        final Thing withoutVersionAttribute =
                withoutAttributes.removeAttribute(JsonFactory.newPointer("model/version"));

        assertThat(withoutVersionAttribute).isSameAs(withoutAttributes);
    }


    @Test
    public void setSameAttributesAgain() {
        final Thing withAttributes =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withAttributes.setAttributes(TestConstants.Thing.ATTRIBUTES)).isSameAs(withAttributes);
    }


    @Test
    public void setSameAttributeAgain() {
        final Thing withAttributes =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(44.673856D);

        final Thing withAttribute = withAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).isSameAs(withAttributes);
    }


    @Test
    public void setAttributesToThingWithoutAttributes() {
        final Thing withoutAttributes = ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, null,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                TestConstants.Thing.MODIFIED);
        final Thing withAttributes = withoutAttributes.setAttributes(TestConstants.Thing.ATTRIBUTES);

        assertThat(withAttributes).hasAttributes(TestConstants.Thing.ATTRIBUTES);
    }


    @Test
    public void removeAttributesFromThingWithoutAttributes() {
        final Thing withoutAttributes = ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, null,
                TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                TestConstants.Thing.MODIFIED);
        final Thing stillWithoutAttributes = withoutAttributes.removeAttributes();

        assertThat(stillWithoutAttributes).isSameAs(withoutAttributes);
    }


    @Test
    public void setFeaturesToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withFeatures = withoutFeatures.setFeatures(TestConstants.Feature.FEATURES);

        assertThat(withFeatures).hasFeatures(TestConstants.Feature.FEATURES);
    }


    @Test
    public void removeFeaturesFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing stillWithoutFeatures = withoutFeatures.removeFeatures();

        assertThat(stillWithoutFeatures).isSameAs(withoutFeatures);
    }


    @Test
    public void setSameFeaturesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withFeatures.setFeatures(TestConstants.Feature.FEATURES)).isSameAs(withFeatures);
    }


    @Test
    public void setFeatureToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withFeature = withoutFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR);

        assertThat(withFeature).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }


    @Test
    public void setSameFeatureAgain() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR)).isSameAs(withFeatures);
    }


    @Test
    public void removeFeatureFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withoutFeatures.removeFeature(TestConstants.Feature.FLUX_CAPACITOR_ID)).isSameAs(withoutFeatures);
    }


    @Test
    public void removeFeature() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withoutFluxCapacitor = withFeatures.removeFeature(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(withoutFluxCapacitor).hasNotFeatureWithId(TestConstants.Feature.FLUX_CAPACITOR_ID);
    }


    @Test
    public void removeNonExistingFeature() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withFeatures.removeFeature("Waldo")).isSameAs(withFeatures);
    }


    @Test
    public void setFeaturePropertiesToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withFluxCapacitor =
                withoutFeatures.setFeatureProperties(
                        TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);

        assertThat(withFluxCapacitor)
                .hasFeatureProperties(TestConstants.Feature.FLUX_CAPACITOR_ID,
                        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }


    @Test
    public void setSameFeaturePropertiesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withFeatures.setFeatureProperties(
                TestConstants.Feature.FLUX_CAPACITOR_ID, TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES))
                .isSameAs(withFeatures);
    }


    @Test
    public void removeFeaturePropertiesFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withoutFeatures.removeFeatureProperties(TestConstants.Feature.FLUX_CAPACITOR_ID)).isSameAs(
                withoutFeatures);
    }


    @Test
    public void removeFeaturePropertiesFromFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(TestConstants.Thing.THING_ID)
                .setAttributes(TestConstants.Thing.ATTRIBUTES)
                .setFeature(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .setPermissions(TestConstants.Thing.ACL)
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setRevision(TestConstants.Thing.REVISION)
                .build();

        assertThat(thing)
                .hasFeatureWithId(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .featureHasNoProperties(TestConstants.Feature.FLUX_CAPACITOR_ID);

        assertThat(thing.removeFeatureProperties(TestConstants.Feature.FLUX_CAPACITOR_ID)).isSameAs(thing);
    }


    @Test
    public void setFeaturePropertyToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withFeature = withoutFeatures
                .setFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                        KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeature)
                .hasFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                        KNOWN_FEATURE_PROPERTY_VALUE);
    }


    @Test
    public void setFeaturePropertyToThingWithFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(TestConstants.Thing.THING_ID)
                .setAttributes(TestConstants.Thing.ATTRIBUTES)
                .setFeature(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .setPermissions(TestConstants.Thing.ACL)
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setRevision(TestConstants.Thing.REVISION)
                .build();

        final Thing withFeatureProperty =
                thing.setFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                        KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeatureProperty)
                .hasFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                        KNOWN_FEATURE_PROPERTY_VALUE);
    }


    @Test
    public void setSameFeaturePropertyAgain() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(
                withFeatures.setFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                        KNOWN_FEATURE_PROPERTY_VALUE))
                .isSameAs(withFeatures);
    }


    @Test
    public void removeFeaturePropertyFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        null, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);

        assertThat(withoutFeatures.removeFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID,
                KNOWN_FEATURE_PROPERTY_PATH))
                .isSameAs(withoutFeatures);
    }


    @Test
    public void removeFeaturePropertyFromFeatureWithoutThisProperty() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(TestConstants.Thing.THING_ID)
                .setAttributes(TestConstants.Thing.ATTRIBUTES)
                .setFeature(TestConstants.Feature.FLUX_CAPACITOR_ID)
                .setPermissions(TestConstants.Thing.ACL)
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setRevision(TestConstants.Thing.REVISION)
                .build();

        assertThat(thing.removeFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID,
                KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(thing);
    }


    @Test
    public void removeFeatureProperty() {
        final Thing withFeatures =
                ImmutableThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.ACL, TestConstants.Thing.ATTRIBUTES,
                        TestConstants.Feature.FEATURES, TestConstants.Thing.LIFECYCLE, TestConstants.Thing.REVISION,
                        TestConstants.Thing.MODIFIED);
        final Thing withoutFeatureProperty =
                withFeatures.removeFeatureProperty(TestConstants.Feature.FLUX_CAPACITOR_ID,
                        KNOWN_FEATURE_PROPERTY_PATH);

        assertThat(withoutFeatureProperty).hasNotFeatureProperty(
                TestConstants.Feature.FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);
    }


    @Test
    public void ensureThingsNewBuilderWorksV1() {
        final Thing thing = Thing.newBuilder()
                .setId(TestConstants.Thing.THING_ID)
                .setPermissions(TestConstants.Thing.ACL)
                .setAttributes(TestConstants.Thing.ATTRIBUTES)
                .setFeatures(TestConstants.Feature.FEATURES)
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setRevision(TestConstants.Thing.REVISION)
                .setModified(TestConstants.Thing.MODIFIED)
                .build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V1);
    }


    @Test
    public void ensureThingsNewBuilderWorksV2() {
        final Thing thing = Thing.newBuilder()
                .setId(TestConstants.Thing.THING_ID)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setAttributes(TestConstants.Thing.ATTRIBUTES)
                .setFeatures(TestConstants.Feature.FEATURES)
                .setLifecycle(TestConstants.Thing.LIFECYCLE)
                .setRevision(TestConstants.Thing.REVISION)
                .setModified(TestConstants.Thing.MODIFIED)
                .build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V2);
    }


    @Test
    public void ensureThingsToBuilderWorksV1() {
        DittoJsonAssertions.assertThat(TestConstants.Thing.THING_V1)
                .isEqualTo(TestConstants.Thing.THING_V1.toBuilder().build());
    }


    @Test
    public void ensureThingsToBuilderWorksV2() {
        DittoJsonAssertions.assertThat(TestConstants.Thing.THING_V2)
                .isEqualTo(TestConstants.Thing.THING_V2.toBuilder().build());
    }


    @Test
    public void ensureThingToJsonContainsNonHiddenFieldsV1() {
        final JsonObject jsonObject = TestConstants.Thing.THING_V1.toJson(JsonSchemaVersion.V_1);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, TestConstants.Thing.THING_ID);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.ATTRIBUTES, TestConstants.Thing.ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.FEATURES, TestConstants.Feature.FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ACL, TestConstants.Thing.ACL.toJson());
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.SCHEMA_VERSION);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.REVISION);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.LIFECYCLE);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.NAMESPACE);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.MODIFIED);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.POLICY_ID);
    }


    @Test
    public void ensureThingToJsonWithSpecialContainsAllFieldsV1() {
        final JsonObject jsonObject =
                TestConstants.Thing.THING_V1.toJson(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.SCHEMA_VERSION, JsonValue.of(JsonSchemaVersion.V_1.toInt()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, TestConstants.Thing.THING_ID);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.ATTRIBUTES, TestConstants.Thing.ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.FEATURES, TestConstants.Feature.FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ACL, TestConstants.Thing.ACL.toJson());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.REVISION, JsonValue.of(TestConstants.Thing.REVISION_NUMBER));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.LIFECYCLE, JsonValue.of(TestConstants.Thing.LIFECYCLE.name()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.NAMESPACE, JsonValue.of(
                TestConstants.Thing.NAMESPACE));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.MODIFIED, JsonValue.of(TestConstants.Thing.MODIFIED.toString()));
    }


    @Test
    public void ensureThingToJsonContainsNonHiddenFieldsV2() {
        final JsonObject jsonObject = TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, TestConstants.Thing.THING_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.ATTRIBUTES, TestConstants.Thing.ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.FEATURES, TestConstants.Feature.FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.SCHEMA_VERSION);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.ACL);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.REVISION);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.NAMESPACE);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.LIFECYCLE);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.MODIFIED);
    }


    @Test
    public void ensureThingToJsonWithSpecialContainsAllFieldsV2() {
        final JsonObject jsonObject =
                TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.SCHEMA_VERSION, JsonValue.of(JsonSchemaVersion.V_2.toInt()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, TestConstants.Thing.THING_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.ATTRIBUTES, TestConstants.Thing.ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.FEATURES, TestConstants.Feature.FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.REVISION, JsonValue.of(TestConstants.Thing.REVISION_NUMBER));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.LIFECYCLE, JsonValue.of(TestConstants.Thing.LIFECYCLE.name()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.NAMESPACE, JsonValue.of(
                TestConstants.Thing.NAMESPACE));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.MODIFIED, JsonValue.of(TestConstants.Thing.MODIFIED.toString()));
    }

}
