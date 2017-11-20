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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;

public class AggregationPersistenceExistsTest extends AbstractVersionedThingSearchPersistenceTestBase {

    private static final String THING1_ID = "thing1";
    private static final String THING1_KNOWN_ATTR = "attr1/a/b/c";
    private static final String THING1_KNOWN_ATTR_VALUE = "thing1";
    private static final String THING1_KNOWN_FEATURE_ID = "feature1";
    private static final String THING1_KNOWN_PROPERTY = "property/a/b/c";
    private static final long THING1_KNOWN_PROPERTY_VALUE = 1;
    private static final String THING2_ID = "thing2";
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

    @Override
    void createTestDataV1() {
        insertDocsV1();
    }

    @Override
    void createTestDataV2() {
        insertDocsV2();
    }

    /** */
    @Test
    public void existsByKnownFeatureId() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByFeatureId(THING2_KNOWN_FEATURE_ID)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void existsByKnownFeatureIdAndProperty() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(
                                fef.existsByFeatureProperty(THING1_KNOWN_FEATURE_ID, THINGS_KNOWN_PROPERTY)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void existsByExactProperty() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByFeatureProperty(THING1_KNOWN_PROPERTY)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID);
    }

    /** */
    @Test
    public void existsByKnownProperty() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByFeatureProperty(THINGS_KNOWN_PROPERTY)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void notExistsByKnownPropertyWithEmptyExpectedResult() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.nor(Collections.singletonList(
                                cf.existsCriteria(fef.existsByFeatureProperty(THINGS_KNOWN_PROPERTY)))))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void notExistsByThing1KnownProperty() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.nor(Collections.singletonList(cf.existsCriteria(fef.existsByFeatureProperty
                                (THING1_KNOWN_PROPERTY)))))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);

        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void existsByUnknownProperty() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByFeatureProperty(THINGS_UNKNOWN_PROPERTY)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void existsByPartOfKnownProperty() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.existsCriteria(fef.existsByFeatureProperty(THINGS_KNOWN_PROPERTY_PART)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void existsByExactAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.existsCriteria(fef.existsByAttribute(THING2_KNOWN_ATTR)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING2_ID);
    }

    /** */
    @Test
    public void existsByKnownAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.existsCriteria(fef.existsByAttribute(THINGS_KNOWN_ATTR)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).containsOnly(THING1_ID, THING2_ID);
    }

    /** */
    @Test
    public void existsByUnknownAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(cf.existsCriteria(fef.existsByAttribute(THINGS_UNKNOWN_ATTR)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    /** */
    @Test
    public void existsByPartOfKnownAttribute() {
        final PolicyRestrictedSearchAggregation aggregation = abf
                .newBuilder(
                        cf.existsCriteria(fef.existsByFeatureProperty(THINGS_KNOWN_ATTR_PART)))
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation);
        assertThat(result).isEmpty();
    }

    private void insertDocsV1() {
        JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING1_KNOWN_ATTR), THING1_KNOWN_ATTR_VALUE);
        final Attributes attributes1 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING2_KNOWN_ATTR), THING2_KNOWN_ATTR_VALUE);
        final Attributes attributes2 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING1_KNOWN_PROPERTY), THING1_KNOWN_PROPERTY_VALUE);
        final Features features1 =
                ThingsModelFactory.newFeatures(Collections.singletonList(ThingsModelFactory
                        .newFeature(THING1_KNOWN_FEATURE_ID,
                                ThingsModelFactory.newFeatureProperties(builder.build()))));

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING2_KNOWN_PROPERTY), THING2_KNOWN_PROPERTY_VALUE);
        final Features features2 =
                ThingsModelFactory.newFeatures(Collections.singletonList(ThingsModelFactory
                        .newFeature(THING2_KNOWN_FEATURE_ID,
                                ThingsModelFactory.newFeatureProperties(builder.build()))));

        final List<Document> documents = Arrays.asList( //
                buildDocV1WithAcl(THING1_ID) //
                        .attributes(attributes1) //
                        .features(features1) //
                        .build(), //
                buildDocV1WithAcl(THING2_ID) //
                        .attributes(attributes2) //
                        .features(features2) //
                        .build() //
        );
        insertDocs(documents);
    }

    private void insertDocsV2() {
        JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING1_KNOWN_ATTR), THING1_KNOWN_ATTR_VALUE);
        final Attributes attributes1 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING2_KNOWN_ATTR), THING2_KNOWN_ATTR_VALUE);
        final Attributes attributes2 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING1_KNOWN_PROPERTY), THING1_KNOWN_PROPERTY_VALUE);
        final Features features1 =
                ThingsModelFactory.newFeatures(Collections.singletonList(ThingsModelFactory
                        .newFeature(THING1_KNOWN_FEATURE_ID,
                                ThingsModelFactory.newFeatureProperties(builder.build()))));

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(THING2_KNOWN_PROPERTY), THING2_KNOWN_PROPERTY_VALUE);
        final Features features2 =
                ThingsModelFactory.newFeatures(Collections.singletonList(ThingsModelFactory
                        .newFeature(THING2_KNOWN_FEATURE_ID,
                                ThingsModelFactory.newFeatureProperties(builder.build()))));

        final List<Document> documents = Arrays.asList( //
                buildDocV2WithGlobalReads(THING1_ID) //
                        .attributes(attributes1) //
                        .features(features1) //
                        .build(), //
                buildDocV2WithGlobalReads(THING2_ID) //
                        .attributes(attributes2) //
                        .features(features2) //
                        .build() //
        );
        insertDocs(documents);

        //attributes
        insertPolicyEntry(THING1_ID + ":attribute/" + THING1_KNOWN_ATTR, "attribute/" +
                THING1_KNOWN_ATTR);
        insertPolicyEntry(THING2_ID + ":attribute/" + THING2_KNOWN_ATTR, "attribute/" +
                THING2_KNOWN_ATTR);

        //features
        insertPolicyEntry(THING1_ID + ":" + THING1_KNOWN_FEATURE_ID, "features/" +
                THING1_KNOWN_FEATURE_ID);
        insertPolicyEntry(THING2_ID + ":" + THING2_KNOWN_FEATURE_ID, "features/" +
                THING2_KNOWN_FEATURE_ID);

        //feature properties
        insertPolicyEntry(THING1_ID + ":" + THING1_KNOWN_FEATURE_ID + "features/properties/" + THING1_KNOWN_PROPERTY,
                "features/properties/" +
                        THING1_KNOWN_PROPERTY);
        insertPolicyEntry(THING2_ID + ":" + THING2_KNOWN_FEATURE_ID + "features/properties/" + THING2_KNOWN_PROPERTY,
                "features/properties/" +
                        THING2_KNOWN_PROPERTY);
    }

}
