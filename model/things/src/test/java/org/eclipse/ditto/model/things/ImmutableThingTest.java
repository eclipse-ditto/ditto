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

import static org.eclipse.ditto.model.things.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.ACL;
import static org.eclipse.ditto.model.things.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.LIFECYCLE;
import static org.eclipse.ditto.model.things.TestConstants.Thing.MODIFIED;
import static org.eclipse.ditto.model.things.TestConstants.Thing.REVISION;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
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

    private static final Thing KNOWN_THING_V1 =
            ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);
    private static final JsonPointer KNOWN_FEATURE_PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue KNOWN_FEATURE_PROPERTY_VALUE = JsonFactory.newValue(1955);

    private static final Thing KNOWN_THING_V2 =
            ImmutableThing.of(THING_ID, TestConstants.Thing.POLICY_ID, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                    MODIFIED);

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
        final Class[] knownImmutableTypes = new Class[]{
                Attributes.class,
                Features.class,
                JsonObject.class,
                AccessControlList.class,
                ThingRevision.class
        };

        assertInstancesOf(ImmutableThing.class,
                areImmutable(),
                provided(knownImmutableTypes).areAlsoImmutable(),
                assumingFields("policyEntries").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void createThingWithoutId() {
        final Thing thing = ImmutableThing.of(null, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(thing)
                .hasNoId()
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutACL() {
        final Thing thing =
                ImmutableThing.of(THING_ID, (AccessControlList) null, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED);

        assertThat(thing)
                .hasId(THING_ID)
                .hasNoAcl()
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutPolicyId() {
        final Thing thing = ImmutableThing.of(THING_ID, (String) null, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                MODIFIED);

        assertThat(thing)
                .hasId(THING_ID)
                .hasNoPolicyId()
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutAttributes() {
        final Thing thing = ImmutableThing.of(THING_ID, ACL, null, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(thing)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasNoAttributes()
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutFeatures() {
        final Thing thing = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);

        assertThat(thing)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasNoFeatures()
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutLifecycle() {
        final Thing thing = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, null, REVISION, MODIFIED);

        assertThat(thing)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasNoLifecycle()
                .hasRevision(REVISION)
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutRevision() {
        final Thing thing = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, null, MODIFIED);

        assertThat(thing)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasNoRevision()
                .hasModified(MODIFIED);
    }

    @Test
    public void createThingWithoutModified() {
        final Thing thing = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, null);

        assertThat(thing)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasNoModified();
    }

    @Test
    public void setAclWorksAsExpected() {
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(ACL)
                .set(ThingsModelFactory.newAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.WRITE))
                .build();

        final Thing changedThing = KNOWN_THING_V1.setAccessControlList(newAcl);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setAclEntryWorksAsExpected() {
        final AclEntry newAclEntry =
                ThingsModelFactory.newAclEntry(TestConstants.Authorization.AUTH_SUBJECT_GRIMES, Permission.WRITE);
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(ACL)
                .set(newAclEntry)
                .build();

        final Thing changedThing = KNOWN_THING_V1.setAclEntry(newAclEntry);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeAllPermissionsWorksAsExpected() {
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(ACL)
                .remove(TestConstants.Authorization.AUTH_SUBJECT_GRIMES)
                .build();

        final Thing changedThing =
                KNOWN_THING_V1.removeAllPermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_GRIMES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setPolicyIdWorksAsExpected() {
        final String newPolicyId = "foo:new";

        final Thing changedThing = KNOWN_THING_V2.setPolicyId(newPolicyId);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(newPolicyId)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.setFeatures(EMPTY_FEATURES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeFeatures();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasNoFeatures()
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setFeaturePropertiesWorksAsExpected() {
        final FeatureProperties newFeatureProperties =
                ThingsModelFactory.newFeaturePropertiesBuilder(FLUX_CAPACITOR_PROPERTIES)
                        .set("target_year_4", 1337)
                        .build();

        final Thing changedThing = KNOWN_THING_V1.setFeatureProperties(FLUX_CAPACITOR_ID, newFeatureProperties);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatureProperties(FLUX_CAPACITOR_ID, newFeatureProperties)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeaturePropertiesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeFeatureProperties(FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .featureHasNoProperties(FLUX_CAPACITOR_ID)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setLifecycleWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.setLifecycle(ThingLifecycle.DELETED);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(ThingLifecycle.DELETED)
                .hasRevision(REVISION);
    }

    @Test
    public void setAttributesWorksAsExpected() {
        final Attributes newAttributes = ThingsModelFactory.newAttributesBuilder()
                .set("manufacturer", "Bosch SI")
                .build();

        final Thing changedThing = KNOWN_THING_V1.setAttributes(newAttributes);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(newAttributes)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");
        final JsonValue newAttributeValue = JsonFactory.newValue("Bosch SI");

        final Thing changedThing = KNOWN_THING_V1.setAttribute(attributePath, newAttributeValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttribute(attributePath, newAttributeValue)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeAttributesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeAttributes();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasNoAttributes()
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");

        final Thing changedThing = KNOWN_THING_V1.removeAttribute(attributePath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasNotAttribute(attributePath)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setFeatureWorksAsExpected() {
        final String newFeatureId = "lamp";
        final Feature newFeature = ThingsModelFactory.newFeature(newFeatureId);

        final Features newFeatures = ThingsModelFactory.newFeaturesBuilder(FEATURES)
                .set(newFeature)
                .build();

        final Thing changedThing = KNOWN_THING_V1.setFeature(newFeature);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(newFeatures)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeatureWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V1.removeFeature(FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_4");
        final JsonValue newPropertyValue = JsonFactory.newValue(1337);

        final Thing changedThing = KNOWN_THING_V1.setFeatureProperty(FLUX_CAPACITOR_ID, propertyPath, newPropertyValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatureProperty(FLUX_CAPACITOR_ID, propertyPath, newPropertyValue)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_2");

        final Thing changedThing =
                KNOWN_THING_V1.removeFeatureProperty(FLUX_CAPACITOR_ID, propertyPath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V1)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasNotFeatureProperty(FLUX_CAPACITOR_ID, propertyPath)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace() {
        ImmutableThing.of("foo.bar:foobar2000", ACL, ATTRIBUTES, EMPTY_FEATURES, LIFECYCLE, REVISION, MODIFIED);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace2() {
        ImmutableThing.of("foo.a42:foobar2000", ACL, ATTRIBUTES, EMPTY_FEATURES, LIFECYCLE, REVISION, MODIFIED);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace3() {
        ImmutableThing.of("ad:foobar2000", ACL, ATTRIBUTES, EMPTY_FEATURES, LIFECYCLE, REVISION, MODIFIED);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace4() {
        ImmutableThing.of("da23:foobar2000", ACL, ATTRIBUTES, EMPTY_FEATURES, LIFECYCLE, REVISION, MODIFIED);
    }

    @Test
    public void setAclEntryToThingWithoutAcl() {
        final Thing withoutAcl =
                ImmutableThing.of(THING_ID, (AccessControlList) null, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED);
        final Thing withAcl = withoutAcl.setAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES);

        assertThat(withAcl).hasAclEntry(TestConstants.Authorization.ACL_ENTRY_GRIMES);
    }

    @Test
    public void setSameAclEntryAgain() {
        final Thing withAcl = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withAcl.setAclEntry(TestConstants.Authorization.ACL_ENTRY_OLDMAN)).isSameAs(withAcl);
    }

    @Test
    public void removeAllPermissionsOfAuthSubjectFromThingWithoutAcl() {
        final Thing withoutAcl =
                ImmutableThing.of(THING_ID, (AccessControlList) null, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED);
        final Thing withoutPermissionsForGrimes =
                withoutAcl.removeAllPermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_GRIMES);

        assertThat(withoutPermissionsForGrimes).isSameAs(withoutAcl);
    }

    @Test
    public void removeAllPermissionsOfAuthSubjectWhichIsNotInAcl() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeatures(FEATURES)
                .setPermissions(TestConstants.Authorization.ACL_ENTRY_OLDMAN)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing.removeAllPermissionsOf(TestConstants.Authorization.AUTH_SUBJECT_GRIMES)).isSameAs(thing);
    }

    @Test
    public void setAttributeToThingWithoutAttributes() {
        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(42.0D);

        final Thing withoutAttributes = ImmutableThing.of(THING_ID, ACL, null, FEATURES, LIFECYCLE, REVISION, MODIFIED);
        final Thing withAttribute = withoutAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).hasAttribute(latitudePath, latitudeValue);
    }

    @Test
    public void removeAttributeFromThingWithoutAttributes() {
        final Thing withoutAttributes = ImmutableThing.of(THING_ID, ACL, null, FEATURES, LIFECYCLE, REVISION, MODIFIED);
        final Thing withoutVersionAttribute =
                withoutAttributes.removeAttribute(JsonFactory.newPointer("model/version"));

        assertThat(withoutVersionAttribute).isSameAs(withoutAttributes);
    }

    @Test
    public void setSameAttributesAgain() {
        final Thing withAttributes =
                ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withAttributes.setAttributes(ATTRIBUTES)).isSameAs(withAttributes);
    }

    @Test
    public void setSameAttributeAgain() {
        final Thing withAttributes = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                MODIFIED);

        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(44.673856D);

        final Thing withAttribute = withAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).isSameAs(withAttributes);
    }

    @Test
    public void setAttributesToThingWithoutAttributes() {
        final Thing withoutAttributes = ImmutableThing.of(THING_ID, ACL, null, FEATURES, LIFECYCLE, REVISION, MODIFIED);
        final Thing withAttributes = withoutAttributes.setAttributes(ATTRIBUTES);

        assertThat(withAttributes).hasAttributes(ATTRIBUTES);
    }

    @Test
    public void removeAttributesFromThingWithoutAttributes() {
        final Thing withoutAttributes = ImmutableThing.of(THING_ID, ACL, null, FEATURES, LIFECYCLE, REVISION,
                MODIFIED);
        final Thing stillWithoutAttributes = withoutAttributes.removeAttributes();

        assertThat(stillWithoutAttributes).isSameAs(withoutAttributes);
    }

    @Test
    public void setFeaturesToThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);
        final Thing withFeatures = withoutFeatures.setFeatures(FEATURES);

        assertThat(withFeatures).hasFeatures(FEATURES);
    }

    @Test
    public void removeFeaturesFromThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);
        final Thing stillWithoutFeatures = withoutFeatures.removeFeatures();

        assertThat(stillWithoutFeatures).isSameAs(withoutFeatures);
    }

    @Test
    public void setSameFeaturesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withFeatures.setFeatures(FEATURES)).isSameAs(withFeatures);
    }

    @Test
    public void setFeatureToThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);
        final Thing withFeature = withoutFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR);

        assertThat(withFeature).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setSameFeatureAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR)).isSameAs(withFeatures);
    }

    @Test
    public void removeFeatureFromThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withoutFeatures.removeFeature(FLUX_CAPACITOR_ID)).isSameAs(withoutFeatures);
    }

    @Test
    public void removeFeature() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);
        final Thing withoutFluxCapacitor = withFeatures.removeFeature(FLUX_CAPACITOR_ID);

        assertThat(withoutFluxCapacitor).hasNotFeatureWithId(FLUX_CAPACITOR_ID);
    }

    @Test
    public void removeNonExistingFeature() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withFeatures.removeFeature("Waldo")).isSameAs(withFeatures);
    }

    @Test
    public void setFeatureDefinitionToThingWithoutFeatures() {
        final FeatureDefinition definition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final Feature expected = ThingsModelFactory.newFeature(FLUX_CAPACITOR_ID, definition);
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);

        final Thing withFluxCapacitor = withoutFeatures.setFeatureDefinition(FLUX_CAPACITOR_ID, definition);

        assertThat(withFluxCapacitor).hasFeature(expected);
    }

    @Test
    public void removeFeatureDefinitionFromExistingFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final Feature expected = ThingsModelFactory.newFeature(featureId);
        final Thing underTest = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, ThingsModelFactory.newFeatures(
                ThingsModelFactory.newFeature(featureId, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)), LIFECYCLE,
                REVISION, MODIFIED);

        assertThat(underTest.removeFeatureDefinition(featureId)).hasFeature(expected);
    }

    @Test
    public void setFeaturePropertiesToThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);
        final Thing withFluxCapacitor =
                withoutFeatures.setFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(withFluxCapacitor).hasFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void setSameFeaturePropertiesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withFeatures.setFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES)).isSameAs(
                withFeatures);
    }

    @Test
    public void removeFeaturePropertiesFromThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withoutFeatures.removeFeatureProperties(FLUX_CAPACITOR_ID)).isSameAs(withoutFeatures);
    }

    @Test
    public void removeFeaturePropertiesFromFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing)
                .hasFeatureWithId(FLUX_CAPACITOR_ID)
                .featureHasNoProperties(FLUX_CAPACITOR_ID);

        assertThat(thing.removeFeatureProperties(FLUX_CAPACITOR_ID)).isSameAs(thing);
    }

    @Test
    public void setFeaturePropertyToThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);
        final Thing withFeature = withoutFeatures.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeature).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyToThingWithFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        final Thing withFeatureProperty = thing.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeatureProperty).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    @Test
    public void setSameFeaturePropertyAgain() {
        final Thing withFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                MODIFIED);

        assertThat(withFeatures.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE)).isSameAs(withFeatures);
    }

    @Test
    public void removeFeaturePropertyFromThingWithoutFeatures() {
        final Thing withoutFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED);

        assertThat(withoutFeatures.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(
                withoutFeatures);
    }

    @Test
    public void removeFeaturePropertyFromFeatureWithoutThisProperty() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(thing);
    }

    @Test
    public void removeFeatureProperty() {
        final Thing withFeatures = ImmutableThing.of(THING_ID, ACL, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                MODIFIED);
        final Thing withoutFeatureProperty =
                withFeatures.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);

        assertThat(withoutFeatureProperty).hasNotFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);
    }

    @Test
    public void ensureThingsNewBuilderWorksV1() {
        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setPermissions(ACL)
                .setAttributes(ATTRIBUTES)
                .setFeatures(FEATURES)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V1);
    }

    @Test
    public void ensureThingsNewBuilderWorksV2() {
        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setAttributes(ATTRIBUTES)
                .setFeatures(FEATURES)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
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
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, THING_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ATTRIBUTES, ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.FEATURES, FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ACL, ACL.toJson());
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
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, THING_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ATTRIBUTES, ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.FEATURES, FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ACL, ACL.toJson());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.REVISION, JsonValue.of(TestConstants.Thing.REVISION_NUMBER));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.LIFECYCLE, JsonValue.of(LIFECYCLE.name()));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.NAMESPACE, JsonValue.of(TestConstants.Thing.NAMESPACE));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.MODIFIED, JsonValue.of(MODIFIED.toString()));
    }

    @Test
    public void ensureThingToJsonContainsNonHiddenFieldsV2() {
        final JsonObject jsonObject = TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, THING_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.ATTRIBUTES, ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.FEATURES, FEATURES.toJson());
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
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, THING_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ATTRIBUTES, ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.FEATURES, FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.REVISION, JsonValue.of(TestConstants.Thing.REVISION_NUMBER));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.LIFECYCLE, JsonValue.of(LIFECYCLE.name()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.NAMESPACE, JsonValue.of(
                TestConstants.Thing.NAMESPACE));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.MODIFIED, JsonValue.of(MODIFIED.toString()));
    }

}
