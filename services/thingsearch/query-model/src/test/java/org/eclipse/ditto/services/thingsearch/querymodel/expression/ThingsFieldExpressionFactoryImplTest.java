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
package org.eclipse.ditto.services.thingsearch.querymodel.expression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Tests {@link ThingsFieldExpressionFactoryImpl}.
 */
public final class ThingsFieldExpressionFactoryImplTest {

    private static final String KNOWN_STRING = "KNOWN_STRING";
    private static final String KNOWN_FEATURE_ID = "feature1";
    private static final String KNOWN_FEATURE = "features/" + KNOWN_FEATURE_ID;
    private static final String KNOWN_FEATURE_PROPERTY = "features/*/properties/" + KNOWN_STRING;
    private static final String KNOWN_FEATURE_PROPERTY_WITH_ID =
            "features/" + KNOWN_FEATURE_ID + "/properties/" + KNOWN_STRING;
    private final ThingsFieldExpressionFactory ef = new ThingsFieldExpressionFactoryImpl();

    /** */
    @Test(expected = NullPointerException.class)
    public void getWithNullPropertyName() {
        ef.filterBy(null);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void getWithUnknownPropertyName() {
        ef.filterBy("unknown");
    }

    /** */
    @Test
    public void getFilterByFeaturePropertyWithId() {
        final FieldExpression fieldExpression = ef.filterBy(KNOWN_FEATURE_PROPERTY_WITH_ID);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeatureIdPropertyExpressionImpl.class);
        final FeatureIdPropertyExpressionImpl featureExpression = (FeatureIdPropertyExpressionImpl) fieldExpression;
        assertThat(featureExpression.getFeatureId()).isEqualTo(KNOWN_FEATURE_ID);
        assertThat(featureExpression.getProperty()).isEqualTo(KNOWN_STRING);
    }

    /** */
    @Test
    public void getFilterByFeaturePropertyWithoutId() {
        final FieldExpression fieldExpression = ef.filterBy(KNOWN_FEATURE_PROPERTY);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeaturePropertyExpressionImpl.class);
        final FeaturePropertyExpressionImpl featureExpression = (FeaturePropertyExpressionImpl) fieldExpression;
        assertThat(featureExpression.getProperty()).isEqualTo(KNOWN_STRING);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void getFilterByFeature() {
        ef.filterBy(KNOWN_FEATURE);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void existsWithNullPropertyName() {
        ef.existsBy(null);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void existsWithUnknownPropertyName() {
        ef.existsBy("unknown");
    }

    /** */
    @Test
    public void existsByFeaturePropertyWithId() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE_PROPERTY_WITH_ID);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeatureIdPropertyExpressionImpl.class);
        final FeatureIdPropertyExpressionImpl featureExpression = (FeatureIdPropertyExpressionImpl) fieldExpression;
        assertThat(featureExpression.getFeatureId()).isEqualTo(KNOWN_FEATURE_ID);
        assertThat(featureExpression.getProperty()).isEqualTo(KNOWN_STRING);
    }

    /** */
    @Test
    public void existsByFeaturePropertyWithoutId() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE_PROPERTY);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeaturePropertyExpressionImpl.class);
        final FeaturePropertyExpressionImpl featureExpression = (FeaturePropertyExpressionImpl) fieldExpression;
        assertThat(featureExpression.getProperty()).isEqualTo(KNOWN_STRING);
    }

    /** */
    @Test
    public void existsByFeature() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeatureExpressionImpl.class);
        final FeatureExpressionImpl featureExpression = (FeatureExpressionImpl) fieldExpression;
        assertThat(featureExpression.getFeatureId()).isEqualTo(KNOWN_FEATURE_ID);
    }

    /** */
    @Test
    public void getSortByFeatureProperty() {
        final FieldExpression fieldExpression = ef.sortBy(KNOWN_FEATURE_PROPERTY_WITH_ID);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeatureIdPropertyExpressionImpl.class);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void getSortByFeaturePropertyInvalid1() {
        ef.sortBy(KNOWN_FEATURE);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void getSortByFeaturePropertyInvalid2() {
        ef.sortBy(KNOWN_FEATURE_PROPERTY);
    }

    /** */
    @Test
    public void getAttribute() {
        final FieldExpression fieldExpression = ef.filterBy(FieldExpressionUtil.addAttributesPrefix(KNOWN_STRING));
        assertThat(fieldExpression).isNotNull().isInstanceOf(AttributeExpressionImpl.class);
        assertThat(((AttributeExpressionImpl) fieldExpression).getKey()).isEqualTo(KNOWN_STRING);
    }


    /** */
    @Test
    public void getThingId() {
        final FieldExpression fieldExpression = ef.filterBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
        assertThat(fieldExpression).isNotNull().isInstanceOf(SimpleFieldExpressionImpl.class);
    }

    /** */
    @Test
    public void filterByFeatureProperty() {
        final FilterFieldExpression fieldExpression = ef.filterByFeatureProperty(KNOWN_STRING);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeaturePropertyExpressionImpl.class);
    }

    /** */
    @Test
    public void filterByFeatureIdAndProperty() {
        final FilterFieldExpression fieldExpression = ef.filterByFeatureProperty(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeatureIdPropertyExpressionImpl.class);
    }

    /** */
    @Test
    public void sortByFeatureProperty() {
        final SortFieldExpression fieldExpression = ef.sortByFeatureProperty(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isNotNull().isInstanceOf(FeatureIdPropertyExpressionImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void filterByFeaturePropertyWithNullKey() {
        ef.filterByFeatureProperty(null);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void sortByFeaturePropertyWithNullKey() {
        ef.sortByFeatureProperty(null, null);
    }

    /** */
    @Test
    public void filterByAttribute() {
        final FilterFieldExpression fieldExpression = ef.filterByAttribute(KNOWN_STRING);
        assertThat(fieldExpression).isNotNull().isInstanceOf(AttributeExpressionImpl.class);
    }

    /** */
    @Test
    public void sortByAttribute() {
        final SortFieldExpression fieldExpression = ef.sortByAttribute(KNOWN_STRING);
        assertThat(fieldExpression).isNotNull().isInstanceOf(AttributeExpressionImpl.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void filterByAttributeWithNullKey() {
        ef.filterByAttribute(null);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void sortByAttributeWithNullKey() {
        ef.sortByAttribute(null);
    }

    /** */
    @Test
    public void filterByThingId() {
        final FilterFieldExpression fieldExpression = ef.filterByThingId();
        assertThat(fieldExpression).isNotNull().isInstanceOf(SimpleFieldExpressionImpl.class);
    }

    /** */
    @Test
    public void sortByThingId() {
        final SortFieldExpression fieldExpression = ef.sortByThingId();
        assertThat(fieldExpression).isNotNull().isInstanceOf(SimpleFieldExpressionImpl.class);
    }
}
