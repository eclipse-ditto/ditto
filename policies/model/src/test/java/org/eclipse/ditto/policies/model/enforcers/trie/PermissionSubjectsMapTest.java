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
package org.eclipse.ditto.policies.model.enforcers.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.enforcers.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.enforcers.trie.PermissionSubjectsMap}.
 */
public final class PermissionSubjectsMapTest {

    private static String subjectId;
    private static String anotherSubjectId;
    private static Set<String> subjectIds;
    private static Set<String> permissions;

    private PermissionSubjectsMap underTest = null;

    @BeforeClass
    public static void initConstants() {
        subjectId = TestConstants.Policy.SUBJECT_ID.toString();

        final SubjectId johnTitor = PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "JohnTitor");
        anotherSubjectId = johnTitor.toString();

        subjectIds = new HashSet<>(2);
        Collections.addAll(subjectIds, subjectId, anotherSubjectId);

        permissions = new HashSet<>(2);
        Collections.addAll(permissions, "READ", "WRITE");
    }

    @Before
    public void setUp() {
        underTest = new PermissionSubjectsMap();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PermissionSubjectsMap.class)
                .usingGetClass()
                .withNonnullFields("data")
                .verify();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToAddAllEntriesFromNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addAllEntriesFrom(null))
                .withMessage("The %s must not be null!", "relation to be added")
                .withNoCause();
    }

    @Test
    public void addAllEntriesFromEmptyMapReturnsSame() {
        final PermissionSubjectsMap permissionSubjectsMap = underTest.addAllEntriesFrom(new PermissionSubjectsMap());

        assertThat(permissionSubjectsMap)
                .isSameAs(underTest)
                .isEmpty();
    }

    @Test
    public void putReturnsNullForNewAssociation() {
        final Map<String, Integer> oldValue =
                underTest.put("READ", Collections.singletonMap(subjectId, 0));

        assertThat(oldValue).isNull();
    }

    @Test
    public void putReturnsOldValueForNewAssociation() {
        final String permission = "READ";
        final Map<String, Integer> weightedSubjectId = Collections.singletonMap(subjectId, 0);
        underTest.put(permission, weightedSubjectId);

        final Map<String, Integer> oldValue = underTest.put(permission, Collections.singletonMap(subjectId, 1));

        assertThat(oldValue).isEqualTo(weightedSubjectId);
    }

    @Test
    public void addTotalRelationOfWeightZeroWorksAsExpected() {
        final Map<String, Integer> weightedSubjectIds = weighSubjectIds(0);

        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);

        assertThat(underTest)
                .hasSameSizeAs(permissions)
                .containsEntry("READ", weightedSubjectIds)
                .containsEntry("WRITE", weightedSubjectIds);
    }

    private static Map<String, Integer> weighSubjectIds(final int weight) {
        final Map<String, Integer> result = new HashMap<>(subjectIds.size());
        subjectIds.forEach(subjectId -> result.put(subjectId, weight));
        return result;
    }

    @Test
    public void getMaxWeightForAllPermissionsReturnsEmptyOptionalForEmptySubjectsMap() {
        final Optional<Integer> maxWeightForAllPermissions =
                underTest.getMaxWeightForAllPermissions(subjectIds, permissions);

        assertThat(maxWeightForAllPermissions).isEmpty();
    }

    @Test
    public void getMaxWeightForAllPermissionsReturnsExpected() {
        final int maxWeight = 42;
        underTest.put("READ", Collections.singletonMap(subjectId, 0));
        underTest.put("READ", Collections.singletonMap(anotherSubjectId, maxWeight));

        final Optional<Integer> maxWeightForAllPermissions =
                underTest.getMaxWeightForAllPermissions(subjectIds, permissions);

        assertThat(maxWeightForAllPermissions).contains(maxWeight);
    }

    @Test
    public void getMaxNonemptyWeightForAllPermissionsReturnsEmptyOptionalIfNotAllPermissionsAreRelated() {
        final int maxWeight = 42;
        underTest.put("READ", Collections.singletonMap(subjectId, 0));
        underTest.put("READ", Collections.singletonMap(anotherSubjectId, maxWeight));

        final Optional<Integer> maxNonemptyWeightForAllPermissions =
                underTest.getMaxNonemptyWeightForAllPermissions(subjectIds, permissions);

        assertThat(maxNonemptyWeightForAllPermissions).isEmpty();
    }

    @Test
    public void getMaxNonemptyWeightForAllPermissionsReturnsExpectedIfAllPermissionsAreRelated() {
        final int maxWeight = 42;
        underTest.put("READ", Collections.singletonMap(subjectId, 0));
        underTest.put("READ", Collections.singletonMap(anotherSubjectId, maxWeight));

        final Optional<Integer> maxNonemptyWeightForAllPermissions =
                underTest.getMaxNonemptyWeightForAllPermissions(subjectIds, Collections.singleton("READ"));

        assertThat(maxNonemptyWeightForAllPermissions).contains(maxWeight);
    }

    @Test
    public void getSubjectUnionReturnsEmptyMapForEmptyPermissions() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);

        final Map<String, Integer> subjectUnion = underTest.getSubjectUnion(Collections.emptySet());

        assertThat(subjectUnion).isEmpty();
    }

    @Test
    public void getSubjectUnionReturnsExpected() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);

        final Map<String, Integer> subjectUnion = underTest.getSubjectUnion(Collections.singleton("WRITE"));

        assertThat(subjectUnion)
                .hasSize(subjectIds.size())
                .containsEntry(subjectId, 0)
                .containsEntry(anotherSubjectId, 0);
    }

    @Test
    public void getSubjectIntersectReturnsEmptyMapIfSubjectIdsAreNotRelatedToAllPermissions() {
        underTest.addTotalRelationOfWeightZero(Collections.singleton("READ"), subjectIds);

        final Map<String, Integer> subjectIntersect =
                underTest.getSubjectIntersect(Collections.singleton("WRITE"));

        assertThat(subjectIntersect).isEmpty();
    }

    @Test
    public void getSubjectIntersectReturnsExpected() {
        final int maxWeight = 23;
        underTest.put("READ", Collections.singletonMap(subjectId, 0));
        underTest.put("READ", Collections.singletonMap(anotherSubjectId, maxWeight));

        final Map<String, Integer> subjectIntersect = underTest.getSubjectIntersect(permissions);

        assertThat(subjectIntersect)
                .hasSize(1)
                .containsEntry(anotherSubjectId, maxWeight);
    }

    @Test
    public void copyOfEmptyRelationReturnsDifferentEmptyRelation() {
        final PermissionSubjectsMap copy = underTest.copy();

        assertThat(copy)
                .isNotSameAs(underTest)
                .isEmpty();
    }

    @Test
    public void copyReturnsExpected() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);

        final PermissionSubjectsMap copy = underTest.copy();

        assertThat(copy)
                .isNotSameAs(underTest)
                .isEqualTo(copy);
    }

    @Test
    public void copyWithIncrementedWeightReturnsExpected() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);

        final PermissionSubjectsMap copyWithIncrementedWeight = underTest.copyWithIncrementedWeight();

        assertThat(copyWithIncrementedWeight)
                .isNotSameAs(underTest)
                .hasSameSizeAs(underTest)
                .containsEntry("READ", weighSubjectIds(1))
                .containsEntry("WRITE", weighSubjectIds(1));
    }

    @Test
    public void copyWithDecrementedWeightReturnsExpected() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);

        final PermissionSubjectsMap copyWithDecrementedWeight = underTest.copyWithDecrementedWeight();

        assertThat(copyWithDecrementedWeight)
                .isNotSameAs(underTest)
                .hasSameSizeAs(underTest)
                .containsEntry("READ", weighSubjectIds(-1))
                .containsEntry("WRITE", weighSubjectIds(-1));
    }

    @Test
    public void removeAllEntriesFromUpdateReturnsExpected() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);
        final PermissionSubjectsMap update = new PermissionSubjectsMap();
        update.addTotalRelationOfWeightZero(Collections.singleton("WRITE"),
                Collections.singleton(anotherSubjectId));

        final PermissionSubjectsMap afterRemoval = underTest.removeAllEntriesFrom(update);

        assertThat(afterRemoval)
                .hasSize(2)
                .containsEntry("READ", weighSubjectIds(0))
                .containsEntry("WRITE", Collections.singletonMap(subjectId, 0));
    }

    @Test
    public void addAllEntriesReturnsExpected() {
        underTest.addTotalRelationOfWeightZero(permissions, subjectIds);
        final int newWeight = 42;
        final PermissionSubjectsMap other = new PermissionSubjectsMap();
        other.put("WRITE", Collections.singletonMap(anotherSubjectId, newWeight));
        final Map<String, Integer> expectedWriteSubjects = new HashMap<>();
        expectedWriteSubjects.put(subjectId, 0);
        expectedWriteSubjects.put(anotherSubjectId, newWeight);

        final PermissionSubjectsMap afterAddition = underTest.addAllEntriesFrom(other);

        assertThat(afterAddition)
                .hasSize(2)
                .containsEntry("READ", weighSubjectIds(0))
                .containsEntry("WRITE", expectedWriteSubjects);
    }

}
