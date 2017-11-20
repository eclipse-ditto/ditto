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
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.junit.Before;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;

/**
 * Tests whether there are also found things which have V1 and Things with V2 in one query.
 */
public class AggregationV1AndV2MergedTest extends AbstractReadPersistenceTestBase {

    private static final String THING_V1_ID1 = ":thingV1_1";
    private static final String THING_V1_ID2 = ":thingV1_2";
    private static final String THING_V2_ID1 = ":thingV2_1";
    private static final String THING_V2_ID2 = ":thingV2_2";

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


    private void insertDocsForComplexCriteria() {
        JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        builder.set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_1_V1);
        builder.set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_1_V1);
        final Attributes attributes1 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_2_V1);
        builder.set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_2_V1);
        final Attributes attributes2 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_1_V2);
        builder.set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_1_V2);
        final Attributes attributes3 = ThingsModelFactory.newAttributes(builder.build());

        builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFactory.newKey(KNOWN_STRING_ATTR), KNOWN_STRING_ATTR_VALUE_THING_2_V2);
        builder.set(JsonFactory.newKey(KNOWN_NUMBER_ATTR), KNOWN_NUMBER_ATTR_VALUE_THING_2_V2);
        final Attributes attributes4 = ThingsModelFactory.newAttributes(builder.build());

        final List<Document> documents = Arrays
                .asList(buildDocV1WithAcl(THING_V1_ID1).attributes(attributes1).build(),
                        buildDocV1WithAcl(THING_V1_ID2).attributes(attributes2).build(),
                        buildDocV2WithGlobalReads(THING_V2_ID1).attributes(attributes3).build(),
                        buildDocV2WithGlobalReads(THING_V2_ID2).attributes(attributes4).build());
        insertDocs(documents);

        insertPolicyEntry(THING_V2_ID1 + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" +
                KNOWN_STRING_ATTR);
        insertPolicyEntry(THING_V2_ID2 + ":attribute/" + KNOWN_STRING_ATTR, "attribute/" +
                KNOWN_STRING_ATTR);
        insertPolicyEntry(THING_V2_ID1 + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" +
                KNOWN_NUMBER_ATTR);
        insertPolicyEntry(THING_V2_ID2 + ":attribute/" + KNOWN_NUMBER_ATTR, "attribute/" +
                KNOWN_NUMBER_ATTR);
    }

    @Before
    @Override
    public void before() {
        super.before();
        insertDocsForComplexCriteria();
    }

    /** */
    @Test
    public void findAllByLtNumber() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceTestBase.fef.filterByAttribute(KNOWN_NUMBER_ATTR),
                        AbstractThingSearchPersistenceTestBase.cf.lt(200)))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_V1_ID1, THING_V1_ID2, THING_V2_ID1);
    }

    /** */
    @Test
    public void findAllByStrings() {
        final PolicyRestrictedSearchAggregation aggregation1 = AbstractThingSearchPersistenceTestBase.abf
                .newBuilder(AbstractThingSearchPersistenceTestBase.cf.or(Arrays.asList(
                        AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                                AbstractThingSearchPersistenceTestBase.fef.filterByAttribute(KNOWN_STRING_ATTR),
                                AbstractThingSearchPersistenceTestBase.cf.eq(KNOWN_STRING_ATTR_VALUE_THING_1_V1)),
                        AbstractThingSearchPersistenceTestBase.cf.fieldCriteria(
                                AbstractThingSearchPersistenceTestBase.fef.filterByAttribute
                                (KNOWN_STRING_ATTR),
                                AbstractThingSearchPersistenceTestBase.cf.eq(KNOWN_STRING_ATTR_VALUE_THING_2_V2)))))
                .authorizationSubjects(AbstractThingSearchPersistenceTestBase.KNOWN_SUBJECTS)
                .build();

        final Collection<String> result = findAll(aggregation1);
        assertThat(result).containsOnly(THING_V1_ID1, THING_V2_ID2);
    }

}
