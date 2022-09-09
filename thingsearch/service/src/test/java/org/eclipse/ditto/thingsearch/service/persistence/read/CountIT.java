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

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.AbstractThingSearchPersistenceITBase;
import org.eclipse.ditto.thingsearch.service.persistence.TestConstants;
import org.junit.Test;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class CountIT extends AbstractReadPersistenceITBase {

    private static final ThingId THING_BASE_ID =
            TestConstants.thingId("thingsearch", "countThing");
    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final String SUDO_NAMESPACE = "sudoThings";

    private final Policy otherPolicy = createOtherPolicy();

    @Test
    public void countAny() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;

        for (int i = 0; i < expectedCount; i++) {
            final ThingId individualThingId = ThingId.of(THING_BASE_ID.getNamespace(), THING_BASE_ID.getName() + i);
            insertThingWithAttribute(individualThingId, KNOWN_STRING_VALUE);
        }

        final long actualCount = executeCount(AbstractThingSearchPersistenceITBase.cf.any());

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    @Test
    public void countAnyWithoutAuthorization() {
        insertThingWithAttribute(THING_BASE_ID, KNOWN_STRING_VALUE);

        final long actualCount = count(
                AbstractThingSearchPersistenceITBase.qbf.newUnlimitedBuilder(
                        AbstractThingSearchPersistenceITBase.cf.any()).build(), Collections.emptyList());

        assertThat(actualCount).isZero();
    }

    @Test
    public void countEquals() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;
        final String attributeValue = UUID.randomUUID().toString();

        for (int i = 0; i < expectedCount; i++) {
            final ThingId individualThingId = ThingId.of(THING_BASE_ID.getNamespace(), THING_BASE_ID.getName() + i);
            insertThingWithAttribute(individualThingId, attributeValue);
        }

        final long actualCount =
                executeCount(AbstractThingSearchPersistenceITBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceITBase.fef.filterByAttribute(KNOWN_ATTRIBUTE_KEY_1),
                        AbstractThingSearchPersistenceITBase.cf.eq(attributeValue)));

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    @Test
    public void countWithNoMatchingResults() {
        final ThingId nonExistingThingId =
                TestConstants.thingId(TestConstants.Thing.NAMESPACE, UUID.randomUUID().toString());

        final long actualCount = executeCount(
                AbstractThingSearchPersistenceITBase.cf.fieldCriteria(
                        AbstractThingSearchPersistenceITBase.fef.filterByThingId(),
                        AbstractThingSearchPersistenceITBase.cf.eq(nonExistingThingId.toString())));

        assertThat(actualCount).isZero();
    }

    @Test
    public void countIgnoresDeletedThings() {
        final Random random = new Random();
        final long count = random.nextInt(100) + 10;
        final long expectedCount = count - 1;

        final List<Thing> thingsToCreate = Stream.iterate(0, i -> i + 1)
                .limit(count)
                .map(i -> createThingV2(ThingId.of(THING_BASE_ID.getNamespace(), THING_BASE_ID.getName() + i)))
                .toList();
        thingsToCreate.forEach(this::persistThingV2);

        // delete one of the things
        deleteThing(thingsToCreate.get(0), 0L);

        final long actualCount = executeCount(AbstractThingSearchPersistenceITBase.cf.any());

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    private void insertThingWithAttribute(final ThingId thingId, final String attributeValue) {
        final Thing thing = createThingV2(thingId);

        persistThing(thing
                .setAttribute(KNOWN_ATTRIBUTE_KEY_1, attributeValue)
                .setAttribute(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE));
    }

    @Override
    protected Policy getPolicy(final ThingId thingId) {
        if (thingId.getNamespace().equals(SUDO_NAMESPACE)) {
            return otherPolicy;
        } else {
            return super.getPolicy(thingId);
        }
    }

    private static Policy createOtherPolicy() {
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .build();
    }

    private long executeCount(final Criteria criteria) {
        return count(AbstractThingSearchPersistenceITBase.qbf.newUnlimitedBuilder(criteria).build());
    }

}
