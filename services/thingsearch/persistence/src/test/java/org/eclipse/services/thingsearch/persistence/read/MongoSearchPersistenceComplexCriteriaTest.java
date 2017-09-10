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
import static org.eclipse.ditto.model.things.ThingsModelFactory.newThingBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.junit.Test;

import org.eclipse.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.eclipse.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class MongoSearchPersistenceComplexCriteriaTest extends AbstractReadPersistenceTestBase {

    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final String OTHER_STRING_VALUE = "otherValue";

    private static final String THING_ID_WITH_KEY_1_AND_2 = "with:1and2";
    private static final String THING_ID_WITH_KEY_1_ONLY = "with:1Only";
    private static final String THING_ID_WITH_KEY_2_ONLY = "with:2Only";
    private static final String THING_ID_WITH_OTHER_VALUE_1 = "with:OtherValue1";
    private static final String THING_ID_WITH_OTHER_VALUE_2 = "with:OtherValue2";
    private static final String THING_ID_WITH_NO_KEY = "with:NoKey";

    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final ThingsFieldExpressionFactory ef = new ThingsFieldExpressionFactoryImpl();

    /** */
    @Test
    public void findAllWithAndCriteria() {
        insertDocsForComplexCriteriaV1();

        final Criteria crit =
                cf.and(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2)));
        final Collection<String> result = findAll(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2);
    }

    /** */
    @Test
    public void findAllWithOrCriteria() {
        insertDocsForComplexCriteriaV1();

        final Criteria crit =
                cf.or(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2)));
        final Collection<String> result = findAll(crit);
        assertThat(result).containsOnly(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY, THING_ID_WITH_KEY_2_ONLY);
    }

    /** */
    @Test
    public void findAllWithNorCriteria() {
        insertDocsForComplexCriteriaV1();

        final Criteria crit =
                cf.nor(Arrays.asList(searchForKey(KNOWN_ATTRIBUTE_KEY_1), searchForKey(KNOWN_ATTRIBUTE_KEY_2)));
        final Collection<String> result = findAll(crit);

        assertThat(result)
                .isNotEmpty()
                .doesNotContain(THING_ID_WITH_KEY_1_AND_2, THING_ID_WITH_KEY_1_ONLY, THING_ID_WITH_KEY_2_ONLY);
    }

    private Criteria searchForKey(final String key) {
        return cf.fieldCriteria(ef.filterByAttribute(key), cf.eq(KNOWN_STRING_VALUE));
    }

    private void insertDocsForComplexCriteriaV1() {
        final Attributes attributes1 = ThingsModelFactory.newAttributesBuilder()
                .set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_1), KNOWN_STRING_VALUE)
                .set(JsonFactory.newKey(KNOWN_ATTRIBUTE_KEY_2), KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes2 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes3 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE)
                .build();

        final Attributes attributes4 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_1, OTHER_STRING_VALUE)
                .build();

        final Attributes attributes5 = ThingsModelFactory.newAttributesBuilder()
                .set(KNOWN_ATTRIBUTE_KEY_2, OTHER_STRING_VALUE)
                .build();

        final List<Thing> things = Arrays.asList(
                newThingBuilder().setId(THING_ID_WITH_KEY_1_AND_2).setAttributes(attributes1).build(),
                newThingBuilder().setId(THING_ID_WITH_KEY_1_ONLY).setAttributes(attributes2).build(),
                newThingBuilder().setId(THING_ID_WITH_KEY_2_ONLY).setAttributes(attributes3).build(),
                newThingBuilder().setId(THING_ID_WITH_OTHER_VALUE_1).setAttributes(attributes4).build(),
                newThingBuilder().setId(THING_ID_WITH_OTHER_VALUE_2).setAttributes(attributes5).build(),
                newThingBuilder().setId(THING_ID_WITH_NO_KEY).build());

        for (final Thing thing : things) {
            final Thing thingWithAcl = insertAcl(thing);
            runBlocking(writePersistence.insertOrUpdate(thingWithAcl, 0, 0));
        }
    }

    private static ThingDocumentBuilder buildDoc(final String thingId) {
        return ThingDocumentBuilder.create(thingId);
    }

    private Thing insertAcl(final Thing thing) {
        final ThingBuilder.FromCopy builder = thing.toBuilder();
        KNOWN_SUBJECTS.forEach(subject ->
                builder.setPermissions(AuthorizationSubject.newInstance(subject),
                        Permission.READ));
        return builder.build();
    }

}
