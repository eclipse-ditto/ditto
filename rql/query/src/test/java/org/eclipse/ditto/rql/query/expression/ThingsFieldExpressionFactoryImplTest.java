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
package org.eclipse.ditto.rql.query.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests {@link ThingsFieldExpressionFactoryImpl}.
 */
public final class ThingsFieldExpressionFactoryImplTest {

    private static final String SLASH = "/";

    private static final String KNOWN_STRING = "KNOWN_STRING";

    private static final String KNOWN_FEATURE_ID = "feature1";

    private static final String KNOWN_FEATURE = "features/" + KNOWN_FEATURE_ID;

    private static final String KNOWN_FEATURE_PROPERTIES = "features/" + KNOWN_FEATURE_ID + "/properties";

    private static final String KNOWN_FEATURE_DESIRED_PROPERTIES =
            "features/" + KNOWN_FEATURE_ID + "/desiredProperties/";

    private static final String KNOWN_FEATURE_PROPERTY_WITH_ID =
            "features/" + KNOWN_FEATURE_ID + "/properties/" + KNOWN_STRING;

    private static final String KNOWN_FEATURE_DESIRED_PROPERTY_WITH_ID =
            "features/" + KNOWN_FEATURE_ID + "/desiredProperties/" + KNOWN_STRING + "_desired";

    private static final String KNOWN_FEATURE_DEFINITION_WITH_ID =
            "features/" + KNOWN_FEATURE_ID + "/definition";

    private static final String KNOWN_METADATA = "_metadata/" + KNOWN_FEATURE_PROPERTY_WITH_ID;

    private static final Map<String, String> SIMPLE_FIELD_MAPPINGS = new HashMap<>();
    static {
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_DEFINITION, FieldExpressionUtil.FIELD_NAME_DEFINITION);
    }

    private final ThingsFieldExpressionFactory ef = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

    @Test(expected = NullPointerException.class)
    public void filterByWithNullPropertyName() {
        ef.filterBy(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void filterByWithUnknownPropertyName() {
        ef.filterBy("unknown");
    }

    @Test
    public void filterByWithFeaturePropertyWithId() {
        final FieldExpression fieldExpression = ef.filterBy(KNOWN_FEATURE_PROPERTY_WITH_ID);

        final FilterFieldExpression expected = new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByWithFeatureDesiredPropertyWithId() {
        final FieldExpression fieldExpression = ef.filterBy(KNOWN_FEATURE_DESIRED_PROPERTY_WITH_ID);

        final FilterFieldExpression expected =
                new FeatureIdDesiredPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING + "_desired");
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByWithFeatureDefinitionWithId() {
        final FieldExpression fieldExpression = ef.filterBy(KNOWN_FEATURE_DEFINITION_WITH_ID);
        final FilterFieldExpression expected = new FeatureDefinitionExpressionImpl(KNOWN_FEATURE_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void filterByWithFeature() {
        ef.filterBy(KNOWN_FEATURE);
    }

    @Test(expected = NullPointerException.class)
    public void existsByWithNullPropertyName() {
        ef.existsBy(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void existsByWithUnknownPropertyName() {
        ef.existsBy("unknown");
    }

    @Test
    public void existsByWithFeaturePropertyWithId() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE_PROPERTY_WITH_ID);

        final ExistsFieldExpression expected = new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void existsByWithFeature() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE);

        final ExistsFieldExpression expected = new FeatureExpressionImpl(KNOWN_FEATURE_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void existsByWithFeatureProperties() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE_PROPERTIES);

        final ExistsFieldExpression expected = new FeatureIdPropertiesExpressionImpl(KNOWN_FEATURE_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void existsByWithFeatureDesiredProperties() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_FEATURE_DESIRED_PROPERTIES);

        final ExistsFieldExpression expected = new FeatureIdDesiredPropertiesExpressionImpl(KNOWN_FEATURE_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void existsByWithJsonPointer() {
        final FieldExpression fieldExpression = ef.existsBy(SLASH + KNOWN_FEATURE);

        final ExistsFieldExpression expected = new FeatureExpressionImpl(KNOWN_FEATURE_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByWithFeatureProperty() {
        final FieldExpression fieldExpression = ef.sortBy(KNOWN_FEATURE_PROPERTY_WITH_ID);

        final SortFieldExpression expected = new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByWithFeatureDesiredProperty() {
        final FieldExpression fieldExpression = ef.sortBy(KNOWN_FEATURE_DESIRED_PROPERTY_WITH_ID);

        final SortFieldExpression expected =
                new FeatureIdDesiredPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING + "_desired");
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByWithJsonPointer() {
        final FieldExpression fieldExpression = ef.sortBy(SLASH + KNOWN_FEATURE_PROPERTY_WITH_ID);

        final SortFieldExpression expected = new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByWithJsonPointerDesired() {
        final FieldExpression fieldExpression = ef.sortBy(SLASH + KNOWN_FEATURE_DESIRED_PROPERTY_WITH_ID);

        final SortFieldExpression expected =
                new FeatureIdDesiredPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING + "_desired");
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sortByWithFeaturePropertyInvalid1() {
        ef.sortBy(KNOWN_FEATURE);
    }

    @Test
    public void filterByWithAttribute() {
        final FieldExpression fieldExpression = ef.filterBy(FieldExpressionUtil.addAttributesPrefix(KNOWN_STRING));

        final FilterFieldExpression expected = new AttributeExpressionImpl(KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByWithThingId() {
        final FieldExpression fieldExpression = ef.filterBy(FieldExpressionUtil.FIELD_NAME_THING_ID);

        final FilterFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByWithJsonPointer() {
        final FieldExpression fieldExpression = ef.filterBy(SLASH + FieldExpressionUtil.FIELD_NAME_THING_ID);

        final FilterFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByFeatureIdAndProperty() {
        final FilterFieldExpression fieldExpression = ef.filterByFeatureProperty(KNOWN_FEATURE_ID, KNOWN_STRING);

        final FilterFieldExpression expected =
                new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByFeatureIdAndDesiredProperty() {
        final FilterFieldExpression fieldExpression = ef.filterByFeatureDesiredProperty(KNOWN_FEATURE_ID, KNOWN_STRING);

        final FilterFieldExpression expected =
                new FeatureIdDesiredPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByFeatureProperty() {
        final SortFieldExpression fieldExpression = ef.sortByFeatureProperty(KNOWN_FEATURE_ID, KNOWN_STRING);

        final SortFieldExpression expected =
                new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByFeatureDesiredProperty() {
        final SortFieldExpression fieldExpression = ef.sortByFeatureDesiredProperty(KNOWN_FEATURE_ID, KNOWN_STRING);

        final SortFieldExpression expected =
                new FeatureIdDesiredPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByAttribute() {
        final FilterFieldExpression fieldExpression = ef.filterByAttribute(KNOWN_STRING);

        final FilterFieldExpression expected = new AttributeExpressionImpl(KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByAttribute() {
        final SortFieldExpression fieldExpression = ef.sortByAttribute(KNOWN_STRING);

        final SortFieldExpression expected = new AttributeExpressionImpl(KNOWN_STRING);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByThingId() {
        final FilterFieldExpression fieldExpression = ef.filterByThingId();

        final FilterFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByThingId() {
        final SortFieldExpression fieldExpression = ef.sortByThingId();

        final SortFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void filterByDefinition() {
        final FilterFieldExpression fieldExpression = ef.filterByDefinition();

        final FilterFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_NAME_DEFINITION);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void sortByDefinition() {
        final SortFieldExpression fieldExpression = ef.sortByDefinition();

        final SortFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_NAME_DEFINITION);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void existsByWithDefinition() {
        final FieldExpression fieldExpression = ef.existsBy(FieldExpressionUtil.FIELD_NAME_DEFINITION);

        final ExistsFieldExpression expected = new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_NAME_DEFINITION);
        assertThat(fieldExpression).isEqualTo(expected);
    }


    @Test
    public void filterByWithMetadata() {
        final FieldExpression fieldExpression = ef.filterBy(KNOWN_METADATA);

        final FilterFieldExpression expected = new MetadataExpressionImpl(KNOWN_FEATURE_PROPERTY_WITH_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

    @Test
    public void existsByWithMetadata() {
        final FieldExpression fieldExpression = ef.existsBy(KNOWN_METADATA);

        final ExistsFieldExpression expected = new MetadataExpressionImpl(KNOWN_FEATURE_PROPERTY_WITH_ID);
        assertThat(fieldExpression).isEqualTo(expected);
    }

}
