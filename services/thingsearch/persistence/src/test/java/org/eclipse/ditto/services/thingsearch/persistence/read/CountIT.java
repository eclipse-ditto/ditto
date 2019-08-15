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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.junit.Test;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class CountIT extends AbstractReadPersistenceITBase {

    private static final ThingId THING_BASE_ID = TestConstants.thingId("thingsearch", "countThing");
    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final String SUDO_NAMESPACE = "sudoThings";

    private final Enforcer otherPolicyEnforcer = PolicyEnforcers.defaultEvaluator(createOtherPolicy());

    @Test
    public void countAny() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;

        for (int i = 0; i < expectedCount; i++) {
            final ThingId individualThingId = ThingId.of(THING_BASE_ID.getNamespace(), THING_BASE_ID.getName() + i);
            insertThingWithAttribute(individualThingId, KNOWN_STRING_VALUE);
        }

        final long actualCount = executeCount(cf.any());

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    @Test
    public void countAnyWithoutAuthorization() {
        insertThingWithAttribute(THING_BASE_ID, KNOWN_STRING_VALUE);

        final long actualCount = count(qbf.newUnlimitedBuilder(cf.any()).build(), Collections.emptyList());

        assertThat(actualCount).isEqualTo(0L);
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
                executeCount(cf.fieldCriteria(
                        fef.filterByAttribute(KNOWN_ATTRIBUTE_KEY_1),
                        cf.eq(attributeValue)));

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    @Test
    public void countWithNoMatchingResults() {
        final ThingId nonExistingThingId =
                TestConstants.thingId(TestConstants.Thing.NAMESPACE, UUID.randomUUID().toString());

        final long actualCount = executeCount(
                cf.fieldCriteria(
                        fef.filterByThingId(),
                        cf.eq(nonExistingThingId.toString())));

        assertThat(actualCount).isEqualTo(0);
    }

    private void insertThingWithAttribute(final ThingId thingId, final String attributeValue) {
        final Thing thing = createThingV1(thingId, KNOWN_SUBJECTS);

        persistThing(thing
                .setAttribute(KNOWN_ATTRIBUTE_KEY_1, attributeValue)
                .setAttribute(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE));
    }

    @Override
    protected Enforcer getPolicyEnforcer(final ThingId thingId) {
        if (thingId.getNamespace().equals(SUDO_NAMESPACE)) {
            return otherPolicyEnforcer;
        } else {
            return super.getPolicyEnforcer(thingId);
        }
    }

    private static Policy createOtherPolicy() {
        return PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .build();
    }

    private long executeCount(final Criteria criteria) {
        return count(qbf.newUnlimitedBuilder(criteria).build());
    }

}
