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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.Thing.NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.thingId;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.junit.Test;

/**
 * Test the exists field expressions against the database.
 */
public final class ExistsIT extends AbstractVersionedThingSearchPersistenceITBase {

    private static final String THING1_ID = thingId(NAMESPACE, "thing1");
    private static final String THING1_KNOWN_ATTR = "attr1/a/b/c";
    private static final String THING1_KNOWN_ATTR_VALUE = "thing1";
    private static final String THING1_KNOWN_FEATURE_ID = "feature1";
    private static final String THING1_KNOWN_PROPERTY = "property/a/b/c";
    private static final long THING1_KNOWN_PROPERTY_VALUE = 1;
    private static final String THING2_ID = thingId(NAMESPACE, "thing2");
    private static final String THING2_KNOWN_ATTR = "attr1/a/b/d";
    private static final String THING2_KNOWN_ATTR_VALUE = "thing2";
    private static final String THING2_KNOWN_FEATURE_ID = "feature2";
    private static final String THING2_KNOWN_PROPERTY = "property/a/b/d";
    private static final long THING2_KNOWN_PROPERTY_VALUE = 2;

    private static final String THINGS_KNOWN_ATTR = "attr1/a/b";
    private static final String THINGS_KNOWN_ATTR_PART = "att";
    private static final String THINGS_UNKNOWN_ATTR = "attr2";
    private static final String THINGS_KNOWN_PROPERTY = "property/a/b";
    private static final String THINGS_KNOWN_PROPERTY_PART = "prop";
    private static final String THINGS_UNKNOWN_PROPERTY = "property2";

    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final ThingsFieldExpressionFactory ef = new ThingsFieldExpressionFactoryImpl();

    @Override
    void createTestDataV1() {
        insertThings();
    }

    @Override
    void createTestDataV2() {
        insertThings();
    }

    /** */
    @Test
    public void existsByKnownFeatureId() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureId(THING2_KNOWN_FEATURE_ID));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void existsByKnownFeatureIdAndProperty() {
        final Criteria crit =
                cf.existsCriteria(ef.existsByFeatureProperty(THING1_KNOWN_FEATURE_ID, THINGS_KNOWN_PROPERTY));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void existsByExactProperty() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperty(THING1_KNOWN_PROPERTY));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void existsByKnownProperty() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperty(THINGS_KNOWN_PROPERTY));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void existsByUnknownProperty() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperty(THINGS_UNKNOWN_PROPERTY));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void existsByPartOfKnownProperty() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperty(THINGS_KNOWN_PROPERTY_PART));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void existsByExactAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THING2_KNOWN_ATTR));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void existsByKnownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THINGS_KNOWN_ATTR));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void existsByUnknownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THINGS_UNKNOWN_ATTR));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void existsByPartOfKnownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperty(THINGS_KNOWN_ATTR_PART));
        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).isEmpty();
    }


    /** */
    @Test
    public void notExistsByKnownPropertyWithEmptyExpectedResult() {
        final Criteria crit = cf.nor(Collections.singletonList(
                cf.existsCriteria(fef.existsByFeatureProperty(THINGS_KNOWN_PROPERTY))));

        final Collection<String> result = executeVersionedQuery(crit);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void notExistsByThing1KnownProperty() {
        final Criteria crit = cf.nor(Collections.singletonList(
                cf.existsCriteria(fef.existsByFeatureProperty(THING1_KNOWN_PROPERTY))));

        final Collection<String> result = executeVersionedQuery(crit);

        assertThat(result).containsOnly(THING2_ID);
    }

    private void insertThings() {
        final Attributes attributes1 = createAttributes(THING1_KNOWN_ATTR, THING1_KNOWN_ATTR_VALUE);
        final Features features1 =
                createFeatures(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY, THING1_KNOWN_PROPERTY_VALUE);

        final Attributes attributes2 = createAttributes(THING2_KNOWN_ATTR, THING2_KNOWN_ATTR_VALUE);
        final Features features2 =
                createFeatures(THING2_KNOWN_FEATURE_ID, THING2_KNOWN_PROPERTY, THING2_KNOWN_PROPERTY_VALUE);

        persistThing(createThing(THING1_ID).setAttributes(attributes1).setFeatures(features1));
        persistThing(createThing(THING2_ID).setAttributes(attributes2).setFeatures(features2));
    }

    private static Attributes createAttributes(final String attributeKey, final String attributeValue) {
        return Attributes.newBuilder()
                .set(attributeKey, attributeValue)
                .build();
    }

    private static Features createFeatures(final String featureId, final CharSequence propertyKey,
            final long propertyValue) {

        final Feature feature = Feature.newBuilder().withId(featureId).build().setProperty(propertyKey, propertyValue);
        return Features.newBuilder().set(feature).build();
    }
}
