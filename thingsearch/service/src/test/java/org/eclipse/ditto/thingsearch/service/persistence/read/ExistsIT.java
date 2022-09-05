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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the exists field expressions against the database.
 */
public final class ExistsIT extends AbstractReadPersistenceITBase {

    private static final ThingId THING1_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing1");
    private static final String THING1_KNOWN_ATTR = "attr1/a/b/c";
    private static final String THING1_KNOWN_ATTR_VALUE = "thing1";
    private static final String THING1_KNOWN_FEATURE_ID = "feature1";
    private static final String THING1_KNOWN_PROPERTY = "property/a/b/c";
    private static final long THING1_KNOWN_PROPERTY_VALUE = 1;
    private static final ThingId THING2_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing2");
    private static final String THING2_KNOWN_ATTR = "attr1/a/b/d";
    private static final String THING2_KNOWN_ATTR_VALUE = "thing2";
    private static final String THING2_KNOWN_FEATURE_ID = "feature2";
    private static final String THING2_KNOWN_PROPERTY = "property/a/b/d";
    private static final long THING2_KNOWN_PROPERTY_VALUE = 2;
    private static final ThingId THING3_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing3");

    private static final String THINGS_KNOWN_ATTR = "attr1/a/b";
    private static final String THINGS_UNKNOWN_ATTR = "attr2";

    private static final String TAGS1 = "tags1";
    private static final String TAGS2 = "tags2";
    private static final String TAGS3 = "tags3";
    private static final String TAGS4 = "tags4";
    private static final String TAGS5 = "tags5";

    private final CriteriaFactory cf = CriteriaFactory.getInstance();
    private final ThingsFieldExpressionFactory ef = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

    @Before
    public void createTestData() {
        insertThings();
    }

    @Test
    public void existsByKnownFeatureId() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureId(THING2_KNOWN_FEATURE_ID));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING2_ID, THING3_ID);
    }

    @Test
    public void existsByKnownFeatureIdAndProperty() {
        final Criteria crit =
                cf.existsCriteria(ef.existsByFeatureProperty(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void existsByKnownFeatureIdAndDesiredProperty() {
        final Criteria crit =
                cf.existsCriteria(ef.existsByFeatureDesiredProperty(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void existsByProperties() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureProperties(THING1_KNOWN_FEATURE_ID));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING1_ID);
    }

    @Test
    public void existsByDesiredProperties() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureDesiredProperties(THING2_KNOWN_FEATURE_ID));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING2_ID, THING3_ID);
    }

    @Test
    public void existsByExactAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THING2_KNOWN_ATTR));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING2_ID);
    }

    @Test
    public void existsByKnownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THINGS_KNOWN_ATTR));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    @Test
    public void existsByUnknownAttribute() {
        final Criteria crit = cf.existsCriteria(ef.existsByAttribute(THINGS_UNKNOWN_ATTR));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).isEmpty();
    }

    @Test
    public void nullAndEmptyValuesExist() {
        final List<List<ThingId>> results = Stream.of(TAGS1, TAGS2, TAGS3, TAGS4, TAGS5)
                .map(tagName -> cf.existsCriteria(ef.existsByAttribute(tagName)))
                .map(this::findForCriteria)
                .map(ArrayList::new)
                .collect(Collectors.toList());

        final List<List<ThingId>> expected = Stream.generate(() -> THING1_ID)
                .limit(5L)
                .map(Collections::singletonList)
                .collect(Collectors.toList());

        assertThat(results).isEqualTo(expected);
    }

    @Test
    public void existsByAnyFeature() {
        final Criteria crit = cf.existsCriteria(ef.existsByFeatureId("*"));
        final Collection<ThingId> result = findForCriteria(crit);
        assertThat(result).isNotEmpty();
    }

    private void insertThings() {
        final Attributes attributes1 = createAttributes(THING1_KNOWN_ATTR, THING1_KNOWN_ATTR_VALUE).toBuilder()
                .setAll(createEmptyAttributes())
                .build();
        final Features features1 =
                createFeatures(THING1_KNOWN_FEATURE_ID, THING1_KNOWN_PROPERTY, THING1_KNOWN_PROPERTY_VALUE);

        final Attributes attributes2 = createAttributes(THING2_KNOWN_ATTR, THING2_KNOWN_ATTR_VALUE);
        final Features features2 =
                createFeatures(THING2_KNOWN_FEATURE_ID, THING2_KNOWN_PROPERTY, THING2_KNOWN_PROPERTY_VALUE);

        persistThing(createThing(THING1_ID).setAttributes(attributes1).setFeatures(features1));
        persistThing(createThing(THING2_ID).setAttributes(attributes2).setFeatures(features2));
        persistThing(createThing(THING3_ID).setFeatures(features2));
    }

    private static Attributes createAttributes(final String attributeKey, final String attributeValue) {
        return Attributes.newBuilder()
                .set(JsonPointer.of(attributeKey), attributeValue)
                .build();
    }

    private static Features createFeatures(final String featureId, final CharSequence propertyKey,
            final long propertyValue) {

        final Feature feature = Feature.newBuilder()
                .withId(featureId)
                .build()
                .setProperty(propertyKey, propertyValue)
                .setDesiredProperty(propertyKey, propertyValue);
        return Features.newBuilder().set(feature).build();
    }

    private static JsonObject createEmptyAttributes() {
        return JsonObject.newBuilder()
                .set(TAGS1, JsonObject.empty())
                .set(TAGS2, JsonFactory.nullLiteral())
                .set(TAGS3, JsonObject.newBuilder().set("foo", JsonFactory.nullLiteral()).build())
                .set(TAGS4, JsonObject.newBuilder().set("foo", JsonObject.empty()).build())
                .set(TAGS5, JsonArray.empty())
                .build();
    }

}
