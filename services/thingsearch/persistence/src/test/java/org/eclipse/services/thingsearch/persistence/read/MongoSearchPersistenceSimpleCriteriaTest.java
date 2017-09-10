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
package org.eclipse.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.eclipse.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Test;

import org.eclipse.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;

/**
 * Tests for search persistence.
 */
public final class MongoSearchPersistenceSimpleCriteriaTest extends AbstractReadPersistenceTestBase {

    private static final String KNOWN_ATTRIBUTE_KEY = "attributeKey";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final int KNOWN_NUMBER_VALUE = 4711;
    private static final boolean KNOWN_BOOLEAN_VALUE = false;

    private static final String THING_ID_WITH_KNOWN_STR_ATTR_VALUE = "withKnownStrAttrValue";
    private static final String THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE = "withKnownNumberAttrValue";
    private static final String THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE = "withKnownBooleanAttrValue";

    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final ThingsFieldExpressionFactory ef = new ThingsFieldExpressionFactoryImpl();

    /** */
    @Test
    public void findAllByAnyAttribute() {
        final String thingId1 = "thing1";
        final String thingId2 = "thing2";
        final List<Document> documents = Arrays.asList(buildDoc(thingId1).build(), buildDoc(thingId2).build());
        insertDocs(documents);

        final Criteria crit = cf.any();
        final Collection<String> result = findAll(crit);
        assertThat(result).containsOnly(thingId1, thingId2);
    }

    /** */
    @Test
    public void findAllByStringAttribute() {
        insertDocsForAttributeCriteria();

        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.eq(KNOWN_STRING_VALUE));
        final Collection<String> result = findAll(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_STR_ATTR_VALUE);
    }

    /** */
    @Test
    public void findAllByNumberAttribute() {
        insertDocsForAttributeCriteria();

        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.eq(KNOWN_NUMBER_VALUE));
        final Collection<String> result = findAll(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE);
    }

    /** */
    @Test
    public void findAllByBooleanAttribute() {
        insertDocsForAttributeCriteria();

        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.eq(KNOWN_BOOLEAN_VALUE));
        final Collection<String> result = findAll(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByStringAttribute() {
        insertDocsForAttributeCriteria();

        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.ne(KNOWN_STRING_VALUE));
        final Collection<String> result = findAll(crit);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_STR_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByNumberAttribute() {
        insertDocsForAttributeCriteria();

        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.ne(KNOWN_NUMBER_VALUE));
        final Collection<String> result = findAll(crit);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE);
    }

    /** */
    @Test
    public void findNeByBooleanAttribute() {
        insertDocsForAttributeCriteria();

        final Criteria crit = cf.fieldCriteria(ef.filterByAttribute(KNOWN_ATTRIBUTE_KEY), cf.ne(KNOWN_BOOLEAN_VALUE));
        final Collection<String> result = findAll(crit);

        assertThat(result).doesNotContain(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE);
    }

    private void insertDocsForAttributeCriteria() {
        final List<Document> documents = Arrays.asList(
                buildDoc(THING_ID_WITH_KNOWN_STR_ATTR_VALUE).attribute(KNOWN_ATTRIBUTE_KEY, KNOWN_STRING_VALUE).build(),
                buildDoc(THING_ID_WITH_KNOWN_NUMBER_ATTR_VALUE).attribute(KNOWN_ATTRIBUTE_KEY, KNOWN_NUMBER_VALUE)
                        .build(),
                buildDoc(THING_ID_WITH_KNOWN_BOOLEAN_ATTR_VALUE).attribute(KNOWN_ATTRIBUTE_KEY, KNOWN_BOOLEAN_VALUE)
                        .build(),
                buildDoc("withOtherAttrValue").attribute(KNOWN_ATTRIBUTE_KEY, "otherValue").build(),
                buildDoc("withOtherIntValue").attribute(KNOWN_ATTRIBUTE_KEY, 4712).build(),
                buildDoc("withOtherBooleanValue").attribute(KNOWN_ATTRIBUTE_KEY, true).build(),
                buildDoc("withNullValue").attribute(KNOWN_ATTRIBUTE_KEY, (String) null).build());
        insertDocs(documents);
    }

    private static ThingDocumentBuilder buildDoc(final String thingId) {
        return ThingDocumentBuilder.create(thingId);
    }

}
