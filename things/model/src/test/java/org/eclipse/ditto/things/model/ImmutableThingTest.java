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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.things.model.TestConstants.Metadata.METADATA;
import static org.eclipse.ditto.things.model.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.CREATED;
import static org.eclipse.ditto.things.model.TestConstants.Thing.DEFINITION;
import static org.eclipse.ditto.things.model.TestConstants.Thing.LIFECYCLE;
import static org.eclipse.ditto.things.model.TestConstants.Thing.MODIFIED;
import static org.eclipse.ditto.things.model.TestConstants.Thing.POLICY_ID;
import static org.eclipse.ditto.things.model.TestConstants.Thing.REVISION;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableThing}.
 */
public final class ImmutableThingTest {

    private static final Features EMPTY_FEATURES = ThingsModelFactory.emptyFeatures();

    private static final JsonPointer KNOWN_FEATURE_PROPERTY_PATH = JsonFactory.newPointer("target_year_1");
    private static final JsonValue KNOWN_FEATURE_PROPERTY_VALUE = JsonFactory.newValue(1955);

    private static final Thing KNOWN_THING_V2 =
            ImmutableThing.of(THING_ID, TestConstants.Thing.POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE,
                    REVISION, MODIFIED, CREATED, METADATA);

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
                ThingDefinition.class,
                Features.class,
                Metadata.class,
                JsonObject.class,
                ThingRevision.class,
                ThingId.class,
                PolicyId.class
        };

        assertInstancesOf(ImmutableThing.class,
                areImmutable(),
                provided(knownImmutableTypes).areAlsoImmutable());
    }

    @Test
    public void createThingWithoutId() {
        final Thing thing = ImmutableThing.of(null, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                MODIFIED, CREATED, METADATA);

        assertThat(thing)
                .hasNoId()
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
    }

    @Test
    public void createThingWithoutPolicyId() {
        final Thing thing =
                ImmutableThing.of(THING_ID, null, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasNoPolicyId()
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
    }


    @Test
    public void createThingWithoutDefinition() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, null, ATTRIBUTES, FEATURES, LIFECYCLE,
                        REVISION, MODIFIED, CREATED, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasNoDefinition()
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
        ;
    }

    @Test
    public void createThingWithoutAttributes() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, null, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasNoAttributes()
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
    }

    @Test
    public void createThingWithoutFeatures() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED,
                        METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasNoFeatures()
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
    }

    @Test
    public void createThingWithoutLifecycle() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, null, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasNoLifecycle()
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
    }

    @Test
    public void createThingWithInvalidPolicyId() {
        final String invalidPolicyId = "namespace:";
        assertThatExceptionOfType(PolicyIdInvalidException.class).isThrownBy(() -> {
            ImmutableThing.of(
                    THING_ID,
                    PolicyId.of(invalidPolicyId),
                    DEFINITION,
                    ATTRIBUTES,
                    FEATURES,
                    LIFECYCLE,
                    REVISION,
                    MODIFIED,
                    CREATED,
                    METADATA);
        });
    }

    @Test
    public void createThingWithInvalidDefinition() {
        final String invalidDefinitionValue = "!namespace:";
        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class).isThrownBy(() -> {
            ThingDefinition invalidDefinition = ImmutableThingDefinition.ofParsed(invalidDefinitionValue);
            ImmutableThing.of(
                    THING_ID,
                    POLICY_ID,
                    invalidDefinition,
                    ATTRIBUTES,
                    FEATURES,
                    LIFECYCLE,
                    REVISION,
                    MODIFIED,
                    CREATED,
                    METADATA);
        });
    }

    @Test
    public void setInvalidThingDefinition() {
        final String validDefinition = "namespace:name:version";
        final String invalidDefinition = "namespace:";

        final Thing thing = ImmutableThing.of(
                THING_ID,
                POLICY_ID,
                ImmutableThingDefinition.ofParsed(validDefinition),
                ATTRIBUTES,
                FEATURES,
                LIFECYCLE,
                REVISION,
                MODIFIED,
                CREATED,
                METADATA);

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class).isThrownBy(
                () -> thing.setDefinition(invalidDefinition));
    }

    @Test
    public void removeThingDefinitionWorkAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.removeDefinition();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasAttributes(ATTRIBUTES)
                .hasNoDefinition()
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void createThingWithoutRevision() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, null, MODIFIED,
                        CREATED, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasNoRevision()
                .hasModified(MODIFIED)
                .hasCreated(CREATED)
                .hasMetadata(METADATA);
    }

    @Test
    public void createThingWithoutModified() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, null,
                        CREATED, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasCreated(CREATED)
                .hasMetadata(METADATA)
                .hasNoModified();
    }

    @Test
    public void createThingWithoutCreated() {
        final Thing thing =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        null, METADATA);

        assertThat(thing)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION)
                .hasModified(MODIFIED)
                .hasNoCreated();
    }

    @Test
    public void setPolicyIdStringWorksAsExpected() {
        final PolicyId newPolicyId = PolicyId.of("foo:new");

        final Thing changedThing = KNOWN_THING_V2.setPolicyId(newPolicyId);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(newPolicyId)
                .hasAttributes(ATTRIBUTES)
                .hasDefinition(DEFINITION)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }


    @Test
    public void setThingDefinitionStringWorksAsExpected() {
        final String newDefinition = "foo:new:version";

        final Thing changedThing = KNOWN_THING_V2.setDefinition(newDefinition);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasAttributes(ATTRIBUTES)
                .hasDefinition(newDefinition)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setPolicyIdWorksAsExpected() {
        final PolicyId newPolicyId = PolicyId.of("foo:new");

        final Thing changedThing = KNOWN_THING_V2.setPolicyId(newPolicyId);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(newPolicyId)
                .hasAttributes(ATTRIBUTES)
                .hasDefinition(DEFINITION)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setThingDefinitionWorksAsExpected() {
        final ImmutableThingDefinition newDefinition = ImmutableThingDefinition.ofParsed("foo:new:crew");

        final Thing changedThing = KNOWN_THING_V2.setDefinition(newDefinition);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasAttributes(ATTRIBUTES)
                .hasDefinition(newDefinition)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.setFeatures(EMPTY_FEATURES);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeaturesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.removeFeatures();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
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

        final Thing changedThing = KNOWN_THING_V2.setFeatureProperties(FLUX_CAPACITOR_ID, newFeatureProperties);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatureProperties(FLUX_CAPACITOR_ID, newFeatureProperties)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeaturePropertiesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.removeFeatureProperties(FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .featureHasNoProperties(FLUX_CAPACITOR_ID)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setLifecycleWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.setLifecycle(ThingLifecycle.DELETED);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
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

        final Thing changedThing = KNOWN_THING_V2.setAttributes(newAttributes);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(newAttributes)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");
        final JsonValue newAttributeValue = JsonFactory.newValue("Bosch SI");

        final Thing changedThing = KNOWN_THING_V2.setAttribute(attributePath, newAttributeValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttribute(attributePath, newAttributeValue)
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeAttributesWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.removeAttributes();

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasNoAttributes()
                .hasFeatures(FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeAttributeWorksAsExpected() {
        final JsonPointer attributePath = JsonFactory.newPointer("maker");

        final Thing changedThing = KNOWN_THING_V2.removeAttribute(attributePath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
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

        final Thing changedThing = KNOWN_THING_V2.setFeature(newFeature);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(newFeatures)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeatureWorksAsExpected() {
        final Thing changedThing = KNOWN_THING_V2.removeFeature(FLUX_CAPACITOR_ID);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatures(EMPTY_FEATURES)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void setFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_4");
        final JsonValue newPropertyValue = JsonFactory.newValue(1337);

        final Thing changedThing = KNOWN_THING_V2.setFeatureProperty(FLUX_CAPACITOR_ID, propertyPath, newPropertyValue);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasFeatureProperty(FLUX_CAPACITOR_ID, propertyPath, newPropertyValue)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void removeFeaturePropertyWorksAsExpected() {
        final JsonPointer propertyPath = JsonFactory.newPointer("target_year_2");

        final Thing changedThing =
                KNOWN_THING_V2.removeFeatureProperty(FLUX_CAPACITOR_ID, propertyPath);

        assertThat(changedThing)
                .isNotSameAs(KNOWN_THING_V2)
                .hasId(THING_ID)
                .hasPolicyId(POLICY_ID)
                .hasDefinition(DEFINITION)
                .hasAttributes(ATTRIBUTES)
                .hasNotFeatureProperty(FLUX_CAPACITOR_ID, propertyPath)
                .hasLifecycle(LIFECYCLE)
                .hasRevision(REVISION);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace() {
        ImmutableThing.of(ThingId.of("foo.bar", "foobar2000"), POLICY_ID, DEFINITION, ATTRIBUTES, EMPTY_FEATURES,
                LIFECYCLE, REVISION, MODIFIED, CREATED, METADATA);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace2() {
        ImmutableThing.of(ThingId.of("foo.a42", "foobar2000"), POLICY_ID, DEFINITION, ATTRIBUTES, EMPTY_FEATURES,
                LIFECYCLE, REVISION, MODIFIED, CREATED, METADATA);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace3() {
        ImmutableThing.of(ThingId.of("ad", "foobar2000"), POLICY_ID, DEFINITION, ATTRIBUTES, EMPTY_FEATURES, LIFECYCLE,
                REVISION, MODIFIED, CREATED, METADATA);
    }

    @Test
    public void tryToCreateThingWithValidThingIdNamespace4() {
        ImmutableThing.of(ThingId.of("da23", "foobar2000"), POLICY_ID, DEFINITION, ATTRIBUTES, EMPTY_FEATURES,
                LIFECYCLE, REVISION, MODIFIED, CREATED, METADATA);
    }

    @Test
    public void setAttributeToThingWithoutAttributes() {
        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(42.0D);

        final Thing withoutAttributes =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, null, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withAttribute = withoutAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).hasAttribute(latitudePath, latitudeValue);
    }

    @Test
    public void removeAttributeFromThingWithoutAttributes() {
        final Thing withoutAttributes =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, null, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withoutVersionAttribute =
                withoutAttributes.removeAttribute(JsonFactory.newPointer("model/version"));

        assertThat(withoutVersionAttribute).isSameAs(withoutAttributes);
    }

    @Test
    public void setSameAttributesAgain() {
        final Thing withAttributes =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withAttributes.setAttributes(ATTRIBUTES)).isSameAs(withAttributes);
    }

    @Test
    public void setSameAttributeAgain() {
        final Thing withAttributes =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);

        final JsonPointer latitudePath = JsonFactory.newPointer("location/latitude");
        final JsonValue latitudeValue = JsonFactory.newValue(44.673856D);

        final Thing withAttribute = withAttributes.setAttribute(latitudePath, latitudeValue);

        assertThat(withAttribute).isSameAs(withAttributes);
    }

    @Test
    public void setAttributesToThingWithoutAttributes() {
        final Thing withoutAttributes =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, null, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withAttributes = withoutAttributes.setAttributes(ATTRIBUTES);

        assertThat(withAttributes).hasAttributes(ATTRIBUTES);
    }

    @Test
    public void removeAttributesFromThingWithoutAttributes() {
        final Thing withoutAttributes =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, null, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);
        final Thing stillWithoutAttributes = withoutAttributes.removeAttributes();

        assertThat(stillWithoutAttributes).isSameAs(withoutAttributes);
    }

    @Test
    public void setFeaturesToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withFeatures = withoutFeatures.setFeatures(FEATURES);

        assertThat(withFeatures).hasFeatures(FEATURES);
    }

    @Test
    public void removeFeaturesFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing stillWithoutFeatures = withoutFeatures.removeFeatures();

        assertThat(stillWithoutFeatures).isSameAs(withoutFeatures);
    }

    @Test
    public void setSameFeaturesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withFeatures.setFeatures(FEATURES)).isSameAs(withFeatures);
    }

    @Test
    public void setFeatureToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withFeature = withoutFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR);

        assertThat(withFeature).hasFeature(TestConstants.Feature.FLUX_CAPACITOR);
    }

    @Test
    public void setSameFeatureAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withFeatures.setFeature(TestConstants.Feature.FLUX_CAPACITOR)).isSameAs(withFeatures);
    }

    @Test
    public void removeFeatureFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withoutFeatures.removeFeature(FLUX_CAPACITOR_ID)).isSameAs(withoutFeatures);
    }

    @Test
    public void removeFeature() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withoutFluxCapacitor = withFeatures.removeFeature(FLUX_CAPACITOR_ID);

        assertThat(withoutFluxCapacitor).hasNotFeatureWithId(FLUX_CAPACITOR_ID);
    }

    @Test
    public void removeNonExistingFeature() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withFeatures.removeFeature("Waldo")).isSameAs(withFeatures);
    }

    @Test
    public void setFeatureDefinitionToThingWithoutFeatures() {
        final FeatureDefinition definition = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
        final Feature expected = ThingsModelFactory.newFeature(FLUX_CAPACITOR_ID, definition);
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        final Thing withFluxCapacitor = withoutFeatures.setFeatureDefinition(FLUX_CAPACITOR_ID, definition);

        assertThat(withFluxCapacitor).hasFeature(expected);
    }

    @Test
    public void removeFeatureDefinitionFromExistingFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final Feature expected = ThingsModelFactory.newFeature(featureId);
        final Thing underTest =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, ThingsModelFactory.newFeatures(
                        ThingsModelFactory.newFeature(featureId, TestConstants.Feature.FLUX_CAPACITOR_DEFINITION)),
                        LIFECYCLE,
                        REVISION, MODIFIED, CREATED, METADATA);

        assertThat(underTest.removeFeatureDefinition(featureId)).hasFeature(expected);
    }

    @Test
    public void setFeaturePropertiesToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withFluxCapacitor =
                withoutFeatures.setFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(withFluxCapacitor).hasFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void setFeatureDesiredPropertiesToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withFluxCapacitor =
                withoutFeatures.setFeatureDesiredProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);

        assertThat(withFluxCapacitor).hasFeatureDesiredProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void setSameFeaturePropertiesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withFeatures.setFeatureProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES)).isSameAs(
                withFeatures);
    }

    @Test
    public void setSameFeatureDesiredPropertiesAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED,
                        CREATED, METADATA);

        assertThat(withFeatures.setFeatureDesiredProperties(FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES)).isSameAs(
                withFeatures);
    }

    @Test
    public void removeFeaturePropertiesFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withoutFeatures.removeFeatureProperties(FLUX_CAPACITOR_ID)).isSameAs(withoutFeatures);
    }

    @Test
    public void removeFeatureDesiredPropertiesFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withoutFeatures.removeFeatureDesiredProperties(FLUX_CAPACITOR_ID)).isSameAs(withoutFeatures);
    }

    @Test
    public void removeFeaturePropertiesFromFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPolicyId(POLICY_ID)
                .setDefinition(DEFINITION)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing)
                .hasFeatureWithId(FLUX_CAPACITOR_ID)
                .featureHasNoProperties(FLUX_CAPACITOR_ID);

        assertThat(thing.removeFeatureProperties(FLUX_CAPACITOR_ID)).isSameAs(thing);
    }

    @Test
    public void removeDesiredFeaturePropertiesFromFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPolicyId(POLICY_ID)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing)
                .hasFeatureWithId(FLUX_CAPACITOR_ID)
                .featureHasNoDesiredProperties(FLUX_CAPACITOR_ID);

        assertThat(thing.removeFeatureDesiredProperties(FLUX_CAPACITOR_ID)).isSameAs(thing);
    }

    @Test
    public void setFeaturePropertyToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withFeature = withoutFeatures.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeature).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    @Test
    public void setFeatureDesiredPropertyToThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);
        final Thing withFeature =
                withoutFeatures.setFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                        KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeature).hasFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    @Test
    public void setFeaturePropertyToThingWithFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPolicyId(POLICY_ID)
                .setDefinition(DEFINITION)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        final Thing withFeatureProperty = thing.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withFeatureProperty).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    @Test
    public void setFeatureDesiredPropertyToThingWithFeatureWithoutProperties() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPolicyId(POLICY_ID)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        final Thing withDesiredProperty = thing.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);

        assertThat(withDesiredProperty).hasFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE);
    }

    @Test
    public void setSameFeaturePropertyAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);

        assertThat(withFeatures.setFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE)).isSameAs(withFeatures);
    }

    @Test
    public void setSameFeatureDesiredPropertyAgain() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);

        assertThat(withFeatures.setFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH,
                KNOWN_FEATURE_PROPERTY_VALUE)).isSameAs(withFeatures);
    }

    @Test
    public void removeFeaturePropertyFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(withoutFeatures.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(
                withoutFeatures);
    }

    @Test
    public void removeFeatureDesiredPropertyFromThingWithoutFeatures() {
        final Thing withoutFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, null, LIFECYCLE, REVISION, MODIFIED,
                        CREATED, METADATA);

        assertThat(
                withoutFeatures.removeFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(
                withoutFeatures);
    }

    @Test
    public void removeFeaturePropertyFromFeatureWithoutThisProperty() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPolicyId(POLICY_ID)
                .setDefinition(DEFINITION)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(thing);
    }

    @Test
    public void removeFeatureDesiredPropertyFromFeatureWithoutThisProperty() {
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttributes(ATTRIBUTES)
                .setFeature(FLUX_CAPACITOR_ID)
                .setPolicyId(POLICY_ID)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .build();

        assertThat(thing.removeFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH)).isSameAs(thing);
    }

    @Test
    public void removeFeatureProperty() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);
        final Thing withoutFeatureProperty =
                withFeatures.removeFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);

        assertThat(withoutFeatureProperty).hasNotFeatureProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);
    }

    @Test
    public void removeFeatureDesiredProperty() {
        final Thing withFeatures =
                ImmutableThing.of(THING_ID, POLICY_ID, DEFINITION, ATTRIBUTES, FEATURES, LIFECYCLE, REVISION,
                        MODIFIED, CREATED, METADATA);
        final Thing withoutDesiredProperty =
                withFeatures.removeFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);

        assertThat(withoutDesiredProperty).hasNotFeatureDesiredProperty(FLUX_CAPACITOR_ID, KNOWN_FEATURE_PROPERTY_PATH);
    }

    @Test
    public void ensureThingsNewBuilderWorksV2() {
        final Thing thing = Thing.newBuilder()
                .setId(THING_ID)
                .setPolicyId(TestConstants.Thing.POLICY_ID)
                .setAttributes(ATTRIBUTES)
                .setDefinition(DEFINITION)
                .setFeatures(FEATURES)
                .setLifecycle(LIFECYCLE)
                .setRevision(REVISION)
                .setModified(MODIFIED)
                .setCreated(CREATED)
                .build();

        assertThat(thing).isEqualTo(TestConstants.Thing.THING_V2);
    }

    @Test
    public void ensureThingsToBuilderWorksV2() {
        DittoJsonAssertions.assertThat(TestConstants.Thing.THING_V2)
                .isEqualTo(TestConstants.Thing.THING_V2.toBuilder().build());
    }

    @Test
    public void ensureThingToJsonContainsNonHiddenFieldsV2() {
        final JsonObject jsonObject = TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2);
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, THING_ID.toString());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID.toString());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.ATTRIBUTES, ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.DEFINITION, JsonValue.of(DEFINITION.toString()));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.FEATURES, FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.REVISION);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.NAMESPACE);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.LIFECYCLE);
        DittoJsonAssertions.assertThat(jsonObject).doesNotContain(Thing.JsonFields.MODIFIED);
    }

    @Test
    public void ensureThingToJsonWithSpecialContainsAllFieldsV2() {
        final JsonObject jsonObject =
                TestConstants.Thing.THING_V2.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ID, THING_ID.toString());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.POLICY_ID, TestConstants.Thing.POLICY_ID.toString());
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.ATTRIBUTES, ATTRIBUTES);
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.DEFINITION, JsonValue.of(DEFINITION.toString()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.FEATURES, FEATURES.toJson());
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.REVISION, JsonValue.of(TestConstants.Thing.REVISION_NUMBER));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.LIFECYCLE, JsonValue.of(LIFECYCLE.name()));
        DittoJsonAssertions.assertThat(jsonObject).contains(Thing.JsonFields.NAMESPACE, JsonValue.of(
                TestConstants.Thing.NAMESPACE));
        DittoJsonAssertions.assertThat(jsonObject)
                .contains(Thing.JsonFields.MODIFIED, JsonValue.of(MODIFIED.toString()));
    }

    @Test
    public void createThingWithInvalidIds() {
        final List<String> invalidThingIds =
                Arrays.asList("", "foobar2000", "foo--bar:foobar2000", "foo.bar%bum:foobar2000",
                        ".namespace:foobar2000", "namespace.:foobar2000", "namespace..invalid:foobar2000",
                        "namespace.42:foobar2000");

        invalidThingIds.forEach(invalidId -> assertThatExceptionOfType(ThingIdInvalidException.class).isThrownBy(
                () -> ThingId.of(invalidId)));

    }

    @Test
    public void serializeMetadata() {
        final JsonObject json = KNOWN_THING_V2.toJson(JsonSchemaVersion.V_2, field -> true);

        final Optional<JsonObject> metadataOptional = json.getValue(Thing.JsonFields.METADATA);

        assertThat(metadataOptional).contains(JsonObject.newBuilder().set("issuedAt", JsonValue.of(0)).build());
    }
}
