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
package org.eclipse.ditto.services.things.persistence.serializer;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.things.TestConstants.Authorization.ACL_ENTRY_GRIMES;
import static org.eclipse.ditto.model.things.TestConstants.Authorization.ACL_ENTRY_OLDMAN;
import static org.eclipse.ditto.model.things.TestConstants.Authorization.AUTH_SUBJECT_GRIMES;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.ACL;
import static org.eclipse.ditto.model.things.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.LIFECYCLE;
import static org.eclipse.ditto.model.things.TestConstants.Thing.MODIFIED;
import static org.eclipse.ditto.model.things.TestConstants.Thing.REVISION;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.things.persistence.serializer.SnapshotTag;
import org.eclipse.ditto.services.things.persistence.serializer.ThingWithSnapshotTag;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.things.persistence.serializer.ThingWithSnapshotTag}.
 */
public final class ThingWithSnapshotTagTest {

    private static final Features EMPTY_FEATURES = ThingsModelFactory.emptyFeatures();
    private static final ThingWithSnapshotTag KNOWN_THING =
            ThingWithSnapshotTag.newInstance(THING_V1, SnapshotTag.PROTECTED);
    private static final JsonPointer KNOWN_FEATURE_PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue KNOWN_FEATURE_PROPERTY_VALUE = JsonFactory.newValue(1955);

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingWithSnapshotTag.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingWithSnapshotTag.class, areImmutable(), provided(Thing.class).isAlsoImmutable());
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullDelegee() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ThingWithSnapshotTag.newInstance(null, SnapshotTag.PROTECTED))
                .withMessage("The %s must not be null!", "delegee")
                .withNoCause();
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceWithNullSnapshotTag() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ThingWithSnapshotTag.newInstance(THING_V1, null))
                .withMessage("The %s must not be null!", "snapshot tag")
                .withNoCause();
    }

    /** */
    @Test
    public void createNewInstanceWhereDelegeeIsTargetReturnsDelegee() {
        assertThat(ThingWithSnapshotTag.newInstance(KNOWN_THING, SnapshotTag.PROTECTED)).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void getSnapshotTagReturnsExpected() {
        assertThat(KNOWN_THING.getSnapshotTag()).isEqualTo(SnapshotTag.PROTECTED);
    }

    /** */
    @Test
    public void getRevisionReturnsExpected() {
        assertThat(KNOWN_THING).hasRevision(REVISION);
    }

    /** */
    @Test
    public void getModifiedReturnsExpected() {
        assertThat(KNOWN_THING).hasModified(MODIFIED);
    }

    /** */
    @Test
    public void getIdReturnsExpected() {
        assertThat(KNOWN_THING).hasId(THING_ID);
    }

    /** */
    @Test
    public void getNamespaceReturnsExpected() {
        assertThat(KNOWN_THING).hasNamespace(THING_V1.getNamespace().orElse(null));
    }

    /** */
    @Test
    public void setAclWorksAsExpected() {
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(ACL)
                .set(ThingsModelFactory.newAclEntry(AUTH_SUBJECT_GRIMES, Permission.WRITE))
                .build();

        final Thing changedThing = KNOWN_THING.setAccessControlList(newAcl);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setAclEntryWorksAsExpected() {
        final AclEntry newAclEntry = ThingsModelFactory.newAclEntry(AUTH_SUBJECT_GRIMES, Permission.WRITE);
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(ACL)
                .set(newAclEntry)
                .build();

        final Thing changedThing = KNOWN_THING.setAclEntry(newAclEntry);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeAllPermissionsWorksAsExpected() {
        final AccessControlList newAcl = ThingsModelFactory.newAclBuilder(ACL)
                .remove(AUTH_SUBJECT_GRIMES)
                .build();

        final Thing changedThing = KNOWN_THING.removeAllPermissionsOf(AUTH_SUBJECT_GRIMES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(newAcl)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING.setFeatures(EMPTY_FEATURES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING.removeFeatures();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasNoFeatures()
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setFeaturePropertiesWorksAsExpected() {
        final FeatureProperties newFeatureProperties =
                ThingsModelFactory.newFeaturePropertiesBuilder(FLUX_CAPACITOR_PROPERTIES)
                        .set("target_year_4", 1337)
                        .build();

        final Thing changedThing = KNOWN_THING.setFeatureProperties(FLUX_CAPACITOR_ID, newFeatureProperties);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatureProperties(FLUX_CAPACITOR_ID, newFeatureProperties)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeFeaturePropertiesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING.removeFeatureProperties(FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .featureHasNoProperties(FLUX_CAPACITOR_ID)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setLifecycleWorksAsExpected() {
        final Thing changedThing = KNOWN_THING.setLifecycle(ThingLifecycle.DELETED);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(ThingLifecycle.DELETED)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setAttributesWorksAsExpected() {
        final Attributes newAttributes = ThingsModelFactory.newAttributesBuilder()
                .set("manufacturer", "Bosch SI")
                .build();

        final Thing changedThing = KNOWN_THING.setAttributes(newAttributes);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(newAttributes)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");
        final JsonValue newAttributeValue = JsonFactory.newValue("Bosch SI");

        final Thing changedThing = KNOWN_THING.setAttribute(attributePath, newAttributeValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttribute(attributePath, newAttributeValue)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeAttributesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING.removeAttributes();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasNoAttributes()
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");

        final Thing changedThing = KNOWN_THING.removeAttribute(attributePath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasNotAttribute(attributePath)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setFeatureWorksAsExpected() {
        final String newFeatureId = "lamp";
        final Feature newFeature = ThingsModelFactory.newFeature(newFeatureId);

        final Features newFeatures = ThingsModelFactory.newFeaturesBuilder(FEATURES)
                .set(newFeature)
                .build();

        final Thing changedThing = KNOWN_THING.setFeature(newFeature);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(newFeatures)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeFeatureWorksAsExpected() {
        final Thing changedThing = KNOWN_THING.removeFeature(FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_4");
        final JsonValue newPropertyValue = JsonFactory.newValue(1337);

        final Thing changedThing = KNOWN_THING.setFeatureProperty(FLUX_CAPACITOR_ID, propertyPath, newPropertyValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasFeatureProperty(FLUX_CAPACITOR_ID, propertyPath, newPropertyValue)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void removeFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_2");

        final Thing changedThing = KNOWN_THING.removeFeatureProperty(FLUX_CAPACITOR_ID, propertyPath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING)
                .hasId(THING_ID)
                .hasAcl(ACL)
                .hasAttributes(ATTRIBUTES)
                .hasNotFeatureProperty(FLUX_CAPACITOR_ID, propertyPath)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    /** */
    @Test
    public void setAclEntryToThingWithoutAcl() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeatures(FEATURES)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutAcl = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);
        final Thing withAcl = withoutAcl.setAclEntry(ACL_ENTRY_GRIMES);

        assertThat(withAcl).hasAclEntry(ACL_ENTRY_GRIMES);
    }

    /** */
    @Test
    public void setSameAclEntryAgain() {
        assertThat(KNOWN_THING.setAclEntry(ACL_ENTRY_OLDMAN)).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void removeAllPermissionsOfAuthSubjectWhichIsNotInAcl() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeatures(FEATURES)
                .setPermissions(ACL_ENTRY_OLDMAN)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();
        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);

        assertThat(thingWithSnapshotTag.removeAllPermissionsOf(AUTH_SUBJECT_GRIMES)).isEqualTo(thingWithSnapshotTag);
    }

    /** */
    @Test
    public void setAttributeToThingWithoutAttributes() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setPermissions(ACL)
                .setFeatures(FEATURES)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();

        final Thing withoutAttributes = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);

        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(42.0D);

        final Thing withAttribute = withoutAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).hasAttribute(latitudePath, latitudeValue);
    }

    /** */
    @Test
    public void removeAttributeFromThingWithoutAttributes() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setPermissions(ACL)
                .setFeatures(FEATURES)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();

        final Thing withoutAttributes = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);
        final Thing withoutVersionAttribute = withoutAttributes.removeAttribute("model/version");

        assertThat(withoutVersionAttribute).isEqualTo(withoutAttributes);
    }

    /** */
    @Test
    public void setSameAttributesAgain() {
        assertThat(KNOWN_THING.setAttributes(ATTRIBUTES)).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void setSameAttributeAgain() {
        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(44.673856D);

        final Thing withAttribute = KNOWN_THING.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void setAttributesToThingWithoutAttributes() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setFeatures(FEATURES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutAttributes = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);
        final Thing withAttributes = withoutAttributes.setAttributes(ATTRIBUTES);

        assertThat(withAttributes).hasAttributes(ATTRIBUTES);
    }

    /** */
    @Test
    public void removeAttributesFromThingWithoutAttributes() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setFeatures(FEATURES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutAttributes = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);
        final Thing stillWithoutAttributes = withoutAttributes.removeAttributes();

        assertThat(stillWithoutAttributes).isEqualTo(withoutAttributes);
    }

    /** */
    @Test
    public void setFeaturesToThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);
        final Thing withFeatures = withoutFeatures.setFeatures(FEATURES);

        assertThat(withFeatures).hasFeatures(FEATURES);
    }

    /** */
    @Test
    public void removeFeaturesFromThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);
        final Thing stillWithoutFeatures = withoutFeatures.removeFeatures();

        assertThat(stillWithoutFeatures).isEqualTo(withoutFeatures);
    }

    /** */
    @Test
    public void setSameFeaturesAgain() {
        assertThat(KNOWN_THING.setFeatures(FEATURES)).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void setFeatureToThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);
        final Thing withFeature = withoutFeatures.setFeature(FLUX_CAPACITOR);

        assertThat(withFeature).hasFeature(FLUX_CAPACITOR);
    }

    /** */
    @Test
    public void setSameFeatureAgain() {
        assertThat(KNOWN_THING.setFeature(FLUX_CAPACITOR)).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void removeFeatureFromThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);

        assertThat(withoutFeatures.removeFeature(FLUX_CAPACITOR_ID)).isEqualTo(withoutFeatures);
    }

    /** */
    @Test
    public void removeFeature() {
        final Thing withoutFluxCapacitor = KNOWN_THING.removeFeature(FLUX_CAPACITOR_ID);

        assertThat(withoutFluxCapacitor).hasNotFeatureWithId(FLUX_CAPACITOR_ID);
    }

    /** */
    @Test
    public void removeNonExistingFeature() {
        assertThat(KNOWN_THING.removeFeature("Waldo")).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void setFeaturePropertiesToThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);
        final Thing withFluxCapacitor = withoutFeatures.setFeatureProperties(FLUX_CAPACITOR_ID,
                FLUX_CAPACITOR_PROPERTIES);

        assertThat(withFluxCapacitor).hasFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);
    }

    /** */
    @Test
    public void setSameFeaturePropertiesAgain() {
        assertThat(KNOWN_THING.setFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES))
                .isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void removeFeaturePropertiesFromThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);

        assertThat(withoutFeatures.removeFeatureProperties(FLUX_CAPACITOR_ID)).isEqualTo(withoutFeatures);
    }

    /** */
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

        assertThat(thing.removeFeatureProperties(FLUX_CAPACITOR_ID)).isEqualTo(thing);
    }

    /** */
    @Test
    public void setFeaturePropertyToThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);
        final Thing withFeature = withoutFeatures.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeature).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    /** */
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

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);

        final Thing withFeatureProperty = thingWithSnapshotTag.setFeatureProperty(FLUX_CAPACITOR_ID,
                KNOWN_FEATURE_PROPERTY_PATH, KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeatureProperty).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    /** */
    @Test
    public void setSameFeaturePropertyAgain() {
        assertThat(KNOWN_THING.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE)).isEqualTo(KNOWN_THING);
    }

    /** */
    @Test
    public void removeFeaturePropertyFromThingWithoutFeatures() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();
        final Thing withoutFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);

        assertThat(withoutFeatures.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH))
                .isEqualTo(withoutFeatures);
    }

    /** */
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
        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(thing, SnapshotTag.UNPROTECTED);

        assertThat(thingWithSnapshotTag.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH))
                .isEqualTo(thingWithSnapshotTag);
    }

    /** */
    @Test
    public void removeFeatureProperty() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPermissions(ACL)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();
        final Thing withFeatures = ThingWithSnapshotTag.newInstance(thing, SnapshotTag.PROTECTED);
        final Thing withoutFeatureProperty = withFeatures.removeFeatureProperty(FLUX_CAPACITOR_ID,
                KNOWN_FEATURE_PROPERTY_PATH);

        assertThat(withoutFeatureProperty).hasNotFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);
    }

}
