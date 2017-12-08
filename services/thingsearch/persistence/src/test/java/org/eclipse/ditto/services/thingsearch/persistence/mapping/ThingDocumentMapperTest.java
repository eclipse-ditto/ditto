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

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
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
import org.junit.Test;

/**
 * Unit test for {@link ThingDocumentMapper}.
 */
public final class ThingDocumentMapperTest {

    private static final String THING_ID = ":myThing1";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final String KEY4 = "key4";
    private static final String KEY5 = "key5";
    private static final String VALUE1 = "value1";
    private static final Long VALUE2 = 5L;
    private static final Boolean VALUE3 = true;
    private static final String VALUE4 = null;
    private static final Double VALUE5 = 123.45;
    private static final String PROP1_KEY = "prop1";
    private static final Long PROP1_VALUE = 777L;
    private static final String FEATURE_ID1 = "feature1";

    @SuppressWarnings("unchecked")
    private static void assertDocumentCorrect(final Document document) {
        assertThat(((List<Document>) document.get(FIELD_INTERNAL)).size()).isEqualTo(8);
        ((List<Document>) document.get(FIELD_INTERNAL)).forEach(doc -> {
            if (doc.get("f") != null) {
                assertThat(doc.get("k"))
                        .isIn(Arrays.asList(FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + PROP1_KEY, null));
                assertThat(doc.get("v")).isIn(Arrays.asList(PROP1_VALUE, null));
                assertThat(doc.get("f")).isIn(Collections.singletonList(FEATURE_ID1));
            } else if (doc.get("k") != null) {
                assertThat(doc.get("k")).isIn(Arrays
                        .asList(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY1,
                                FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY2,
                                FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY3,
                                FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY4,
                                FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY5));
                assertThat(doc.get("v")).isIn(Arrays.asList(VALUE1, VALUE2, VALUE3, VALUE4, VALUE5));
            } else {
                assertThat(doc.get(FIELD_ACL)).isEqualTo("newSid");
            }
        });
        assertThat(document.get(FIELD_ID)).isEqualTo(THING_ID);
        System.out.println(document.toJson());
    }

    /** */
    @Test
    public void buildThing() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(KEY1), VALUE1);
        builder.set(JsonFactory.newKey(KEY2), VALUE2);
        builder.set(JsonFactory.newKey(KEY3), VALUE3);
        builder.set(JsonFactory.newKey(KEY4), VALUE4);
        builder.set(JsonFactory.newKey(KEY5), VALUE5);

        final JsonObjectBuilder propertiesBuilder = JsonFactory.newObjectBuilder();
        propertiesBuilder.set(JsonFactory.newKey(PROP1_KEY), PROP1_VALUE);
        final FeatureProperties properties = ThingsModelFactory.newFeatureProperties(propertiesBuilder.build());
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, properties);
        final Features features = ThingsModelFactory.newFeatures(Collections.singletonList(feature));

        final AclEntry aclEntry = ThingsModelFactory.newAclEntry(AuthorizationModelFactory.newAuthSubject("newSid"),
                ThingsModelFactory.newPermissions(Permission.READ, Permission.WRITE, Permission.ADMINISTRATE));
        final AccessControlList acl = ThingsModelFactory.newAcl(aclEntry);

        final Attributes attributes = ThingsModelFactory.newAttributes(builder.build());
        final Thing thing =
                ThingsModelFactory.newThingBuilder().setId(THING_ID).setPermissions(acl).setAttributes(attributes)
                        .setFeatures(features).setLifecycle(ThingLifecycle.ACTIVE).setRevision(0L).build();

        final Document mappedDoc = ThingDocumentMapper.toDocument(thing);
        assertDocumentCorrect(mappedDoc);
    }

}
