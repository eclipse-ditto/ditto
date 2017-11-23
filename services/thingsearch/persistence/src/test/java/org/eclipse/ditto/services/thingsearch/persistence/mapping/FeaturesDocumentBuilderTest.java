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
package org.eclipse.ditto.services.thingsearch.persistence.mapping;

import java.util.Collections;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.junit.Test;

/**
 * Tests {@link FeaturesDocumentBuilder}.
 */
public class FeaturesDocumentBuilderTest {

    private static final String FEATURE_ID1 = "feature1";
    private static final String PROP1_KEY = "prop1";
    private static final String FEATURE_ID_WITH_SPECIAL_CHARS = "$org.eclipse.ditto.myfeature";
    private static final String PROP_KEY_WITH_SPECIAL_CHARS = "$org.eclipse.ditto.myproperty";
    private static final String PROP_VALUE = "prop1Value";

    @Test
    public void addFeatures() {
        final JsonObjectBuilder propertiesBuilder = JsonFactory.newObjectBuilder();
        propertiesBuilder.set(JsonFactory.newKey(PROP1_KEY), PROP_VALUE);
        final FeatureProperties properties = ThingsModelFactory.newFeatureProperties(propertiesBuilder.build());
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, properties);
        final Features features = ThingsModelFactory.newFeatures(Collections.singletonList(feature));
        final FeaturesDocumentBuilder builder = FeaturesDocumentBuilder.create();
        builder.features(features);

        final Document doc = builder.build();

        Assertions.assertThat(((List<Document>) doc.get(PersistenceConstants.FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + PROP1_KEY);
        final Document featuresDoc = (Document) doc.get(PersistenceConstants.FIELD_FEATURES);
        final Document featureDoc = (Document) featuresDoc.get(FEATURE_ID1);
        final Document propertiesDoc = (Document) featureDoc.get(PersistenceConstants.FIELD_PROPERTIES);
        Assertions.assertThat(propertiesDoc.get(PROP1_KEY)).isEqualTo(PROP_VALUE);
    }

    @Test
    public void addFeaturesWithSpecialChars() {
        final JsonObjectBuilder propertiesBuilder = JsonFactory.newObjectBuilder();
        propertiesBuilder.set(JsonFactory.newKey(PROP_KEY_WITH_SPECIAL_CHARS), PROP_VALUE);
        final FeatureProperties properties = ThingsModelFactory.newFeatureProperties(propertiesBuilder.build());
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID_WITH_SPECIAL_CHARS, properties);
        final Features features = ThingsModelFactory.newFeatures(Collections.singletonList(feature));
        final FeaturesDocumentBuilder builder = FeaturesDocumentBuilder.create();
        builder.features(features);

        final Document doc = builder.build();

        Assertions.assertThat(((List<Document>) doc.get(PersistenceConstants.FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + PROP_KEY_WITH_SPECIAL_CHARS);
        final Document featuresDoc = (Document) doc.get(PersistenceConstants.FIELD_FEATURES);
        final Document featureDoc = (Document) featuresDoc.get(KeyEscapeUtil.escape(FEATURE_ID_WITH_SPECIAL_CHARS));
        final Document propertiesDoc = (Document) featureDoc.get(PersistenceConstants.FIELD_PROPERTIES);
        Assertions.assertThat(propertiesDoc.get(KeyEscapeUtil.escape(PROP_KEY_WITH_SPECIAL_CHARS))).isEqualTo(PROP_VALUE);
    }
}
