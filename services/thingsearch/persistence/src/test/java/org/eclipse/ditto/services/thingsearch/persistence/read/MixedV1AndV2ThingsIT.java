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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.things.Attributes;
import org.junit.Before;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;

/**
 * Tests whether there are also found things which have V1 and Things with V2 in one query.
 */
public class MixedV1AndV2ThingsIT extends AbstractReadPersistenceITBase {

    private static final String THING_V1_ID1 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingV1_1");
    private static final String THING_V1_ID2 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingV1_2");
    private static final String THING_V2_ID1 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingV2_1");
    private static final String THING_V2_ID2 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingV2_2");

    private static final String KNOWN_NUMBER_ATTR = "magicNo";
    private static final String KNOWN_STRING_ATTR = "cuttingEdge";

    private static final String KNOWN_STRING_ATTR_VALUE_THING_1_V1 = "reactive";
    private static final String KNOWN_STRING_ATTR_VALUE_THING_2_V1 = "functional";
    private static final String KNOWN_STRING_ATTR_VALUE_THING_1_V2 = "nonblocking";
    private static final String KNOWN_STRING_ATTR_VALUE_THING_2_V2 = "resilient";

    private static final int KNOWN_NUMBER_ATTR_VALUE_THING_1_V1 = 23;
    private static final int KNOWN_NUMBER_ATTR_VALUE_THING_2_V1 = 42;
    private static final int KNOWN_NUMBER_ATTR_VALUE_THING_1_V2 = 101;
    private static final int KNOWN_NUMBER_ATTR_VALUE_THING_2_V2 = 1337;

    @Before
    @Override
    public void before() {
        super.before();
        insertThings();
    }

    private void insertThings() {
        final Attributes attributes1 = Attributes.newBuilder()
                .set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_1_V1)
                .set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_1_V1)
                .build();

        final Attributes attributes2 = Attributes.newBuilder()
                .set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_2_V1)
                .set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_2_V1)
                .build();

        final Attributes attributes3 = Attributes.newBuilder()
                .set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_1_V2)
                .set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_1_V2)
                .build();

        final Attributes attributes4 = Attributes.newBuilder()
                .set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_2_V2)
                .set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_2_V2)
                .build();

        persistThingV1(createThingV1(THING_V1_ID1).setAttributes(attributes1));
        persistThingV1(createThingV1(THING_V1_ID2).setAttributes(attributes2));
        persistThingV2(createThingV2(THING_V2_ID1).setAttributes(attributes3));
        persistThingV2(createThingV2(THING_V2_ID2).setAttributes(attributes4));
    }

    @Test
    public void findAllByLtNumber() {
        final Query query =
                qbf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KNOWN_NUMBER_ATTR), cf.lt(200))).build();

        final Collection<String> result = findAll(query, KNOWN_SUBJECTS);
        assertThat(result).containsOnly(THING_V1_ID1, THING_V1_ID2, THING_V2_ID1);
    }


    @Test
    public void findAllByStrings() {
        final Query query = qbf.newBuilder(
                cf.or(Arrays.asList(

                        cf.fieldCriteria(
                                fef.filterByAttribute(KNOWN_STRING_ATTR),
                                cf.eq(KNOWN_STRING_ATTR_VALUE_THING_1_V1)),

                        cf.fieldCriteria(
                                fef.filterByAttribute(KNOWN_STRING_ATTR),
                                cf.eq(KNOWN_STRING_ATTR_VALUE_THING_2_V2))
                )))
                .build();

        final Collection<String> result = findAll(query, KNOWN_SUBJECTS);
        assertThat(result).containsOnly(THING_V1_ID1, THING_V2_ID2);
    }

}
