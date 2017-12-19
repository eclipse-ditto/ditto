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
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.Thing.NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.thingId;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.junit.Test;

/**
 * Tests for complex search criteria on the persistence.
 */
public final class CountIT extends AbstractVersionedThingSearchPersistenceITBase {

    private static final List<String> UNKNOWN_SUBJECTS = Collections.singletonList("any:other_user");
    private static final String UNKNOWN_POLICY_ID = "any:other_policy";

    private static final String THING_BASE_ID = thingId("thingsearch", "countThing");
    private static final String KNOWN_ATTRIBUTE_KEY_1 = "attributeKey1";
    private static final String KNOWN_ATTRIBUTE_KEY_2 = "attributeKey2";

    private static final String KNOWN_STRING_VALUE = "value";
    private static final String SUDO_NAMESPACE = "sudoThings";

    private final PolicyEnforcer otherPolicyEnforcer = PolicyEnforcers.defaultEvaluator(createOtherPolicy());

    @Override
    void createTestDataV1() {
        // test-data are created in tests
    }

    @Override
    void createTestDataV2() {
        // test-data are created in tests
    }

    /** */
    @Test
    public void countAny() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;

        for (int i = 0; i < expectedCount; i++) {
            insertThingWithAttribute(THING_BASE_ID + i, KNOWN_STRING_VALUE, true);
        }

        final long actualCount = executeVersionedCountQuery(cf.any());

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    /** */
    @Test
    public void countAnyWithSudo() {
        // sudo test only needed for V_2 Policy related searches
        if (JsonSchemaVersion.V_2.equals(getVersion())) {
            final Random random = new Random();
            final long expectedCount = random.nextInt(100) + 10;
            final String countThingId = thingId(SUDO_NAMESPACE, "countThing");

            for (int i = 0; i < expectedCount; i++) {
                insertThingWithAttribute(countThingId + i, KNOWN_STRING_VALUE, false);
            }

            // verify nothing is found without sudo
            final long withoutSudoCount = executeVersionedCountQuery(cf.any());
            assertThat(withoutSudoCount).isEqualTo(0L);

            // verify everything is found with sudo
            final long actualCount = executeVersionedCountQuery(cf.any(), true);

            assertThat(actualCount).isEqualTo(expectedCount);
        }
    }

    /** */
    @Test
    public void countEquals() {
        final Random random = new Random();
        final long expectedCount = random.nextInt(100) + 10;
        final String attributeValue = UUID.randomUUID().toString();

        for (int i = 0; i < expectedCount; i++) {
            insertThingWithAttribute(THING_BASE_ID + i, attributeValue, true);
        }

        final long actualCount =
                executeVersionedCountQuery(cf.fieldCriteria(
                        fef.filterByAttribute(KNOWN_ATTRIBUTE_KEY_1),
                        cf.eq(attributeValue)));

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    /** */
    @Test
    public void countWithNoMatchingResults() {
        final String nonExistingThingId = thingId(NAMESPACE, UUID.randomUUID().toString());

        final long actualCount = executeVersionedCountQuery(
                cf.fieldCriteria(
                        fef.filterByThingId(),
                        cf.eq(nonExistingThingId)));

        assertThat(actualCount).isEqualTo(0);
    }

    private void insertThingWithAttribute(final String thingId, final String attributeValue, final boolean known) {
        final Thing thing;
        if (JsonSchemaVersion.V_1.equals(getVersion())) {
            thing = createThingV1(thingId, known ? KNOWN_SUBJECTS : UNKNOWN_SUBJECTS);
        } else {
            thing = createThingV2(thingId, known ? POLICY_ID : UNKNOWN_POLICY_ID);
        }

        persistThing(thing
                .setAttribute(KNOWN_ATTRIBUTE_KEY_1, attributeValue)
                .setAttribute(KNOWN_ATTRIBUTE_KEY_2, KNOWN_STRING_VALUE));
    }

    @Override
    protected PolicyEnforcer getPolicyEnforcer(final String thingId) {
        if (thingId.startsWith(SUDO_NAMESPACE + ":")) {
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


    private long executeVersionedCountQuery(final Criteria criteria) {
        return executeVersionedCountQuery(criteria, false);
    }

    private long executeVersionedCountQuery(final Criteria criteria, final boolean sudo) {
        return executeVersionedQuery(
                crit -> qbf.newUnlimitedBuilder(crit).build(),
                crit -> abf.newCountBuilder(crit)
                        .sudo(sudo)
                        .authorizationSubjects(sudo ? Collections.emptyList() : KNOWN_SUBJECTS)
                        .build(),
                this::count,
                this::aggregateCount,
                criteria);
    }


}
