/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link FeaturesDocumentBuilder}.
 */
public final class FeaturesDocumentBuilderTest {

    private static final String FEATURE_ID1 = "feature1";
    private static final String PROP1_KEY = "prop1";
    private static final String PROP_KEY_WITH_SPECIAL_CHARS = "$org.eclipse.ditto.myproperty";
    private static final String PROP_VALUE_STRING = "prop1Value";
    private static final String PROP_KEY_BOOL = "a_boolean";
    private static final boolean PROP_VALUE_BOOL = true;
    private static final String PROP_KEY_INT = "an_integer";
    private static final int PROP_VALUE_INT = Integer.MAX_VALUE;
    private static final String PROP_KEY_LONG = "a_long";
    private static final long PROP_VALUE_LONG = Long.MIN_VALUE;
    private static final String PROP_KEY_DOUBLE = "a_double";
    private static final double PROP_VALUE_DOUBLE = 23.42D;
    private static final String PROP_KEY_OBJECT = "an_object";
    private static final JsonObject PROP_VALUE_OBJECT = JsonObject.newBuilder()
            .set("key", "value")
            .set("foo", "bar")
            .set("on", true)
            .build();
    private static final String FEATURE_ID_WITH_SPECIAL_CHARS = "$org.eclipse.ditto.myfeature";

    private static FeatureProperties featurePropertiesFixture;

    private FeaturesDocumentBuilder underTest;

    @BeforeClass
    public static void initTestConstants() {
        featurePropertiesFixture = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP1_KEY, PROP_VALUE_STRING)
                .set(PROP_KEY_BOOL, PROP_VALUE_BOOL)
                .set(PROP_KEY_INT, PROP_VALUE_INT)
                .set(PROP_KEY_LONG, PROP_VALUE_LONG)
                .set(PROP_KEY_DOUBLE, PROP_VALUE_DOUBLE)
                .set(PROP_KEY_OBJECT, PROP_VALUE_OBJECT)
                .build();
    }

    @Before
    public void setUp() {
        underTest = FeaturesDocumentBuilder.create();
    }

    @Test
    public void addFeatures() {
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, featurePropertiesFixture);
        final Features features = ThingsModelFactory.newFeatures(feature);
        underTest.features(features);

        final Document doc = underTest.build();

        assertThat(((List<Document>) doc.get(PersistenceConstants.FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + PROP1_KEY);

        final Document featuresDoc = (Document) doc.get(PersistenceConstants.FIELD_FEATURES);
        final Document featureDoc = (Document) featuresDoc.get(FEATURE_ID1);
        final Document propertiesDoc = (Document) featureDoc.get(PersistenceConstants.FIELD_PROPERTIES);

        assertThat(propertiesDoc.get(PROP1_KEY)).isEqualTo(PROP_VALUE_STRING);
    }

    @Test
    public void addFeaturesWithSpecialChars() {
        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP_KEY_WITH_SPECIAL_CHARS, PROP_VALUE_STRING)
                .build();
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID_WITH_SPECIAL_CHARS, featureProperties);
        final Features features = ThingsModelFactory.newFeatures(feature);
        underTest.features(features);

        final Document doc = underTest.build();

        assertThat(((List<Document>) doc.get(PersistenceConstants.FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + PROP_KEY_WITH_SPECIAL_CHARS);

        final Document featuresDoc = (Document) doc.get(PersistenceConstants.FIELD_FEATURES);
        final Document featureDoc = (Document) featuresDoc.get(KeyEscapeUtil.escape(FEATURE_ID_WITH_SPECIAL_CHARS));
        final Document propertiesDoc = (Document) featureDoc.get(PersistenceConstants.FIELD_PROPERTIES);

        assertThat(propertiesDoc.get(KeyEscapeUtil.escape(PROP_KEY_WITH_SPECIAL_CHARS))).isEqualTo(PROP_VALUE_STRING);
    }



}
