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
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.enforcers.trie.GrantRevokeIndex}.
 */
public final class GrantRevokeIndexTest {

    private static String subjectId;
    private static String anotherSubjectId;
    private static Set<String> permissions;

    private PermissionSubjectsMap grantedMap = null;
    private PermissionSubjectsMap revokedMap = null;
    private GrantRevokeIndex underTest = null;

    @BeforeClass
    public static void initTestConstants() {
        subjectId = TestConstants.Policy.SUBJECT_ID.toString();

        final SubjectId johnTitor = PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "JohnTitor");
        anotherSubjectId = johnTitor.toString();

        permissions = new HashSet<>(2);
        Collections.addAll(permissions, "READ", "WRITE");
    }

    @Before
    public void initTestVariables() {
        final Map<String, Integer> readGrantedSubjects = new HashMap<>(2);
        readGrantedSubjects.put(subjectId, 2);
        readGrantedSubjects.put(anotherSubjectId, 1);

        final Map<String, Integer> writeGrantedSubjects = new HashMap<>(1);
        writeGrantedSubjects.put(subjectId, 1);

        final Map<String, Integer> readRevokedSubjects = new HashMap<>(1);
        readRevokedSubjects.put(anotherSubjectId, 2);

        final Map<String, Integer> writeRevokedSubjects = new HashMap<>(1);
        writeRevokedSubjects.put(anotherSubjectId, 1);

        grantedMap = new PermissionSubjectsMap();
        grantedMap.put("READ", readGrantedSubjects);
        grantedMap.put("WRITE", writeGrantedSubjects);

        revokedMap = new PermissionSubjectsMap();
        revokedMap.put("READ", readRevokedSubjects);
        revokedMap.put("WRITE", writeRevokedSubjects);

        underTest = new GrantRevokeIndex(grantedMap, revokedMap);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GrantRevokeIndex.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getGrantedReturnsExpected() {
        assertThat(underTest.getGranted()).isEqualTo(grantedMap);
    }

    @Test
    public void getRevokedReturnsExpected() {
        assertThat(underTest.getRevoked()).isEqualTo(revokedMap);
    }

    @Test
    public void copyWithDecrementedWeightReturnsExpected() {
        final GrantRevokeIndex copyWithDecrementedWeight = underTest.copyWithDecrementedWeight();
        final PermissionSubjectsMap grantedWithDecrementedWeight = copyWithDecrementedWeight.getGranted();
        final PermissionSubjectsMap revokedWithDecrementedWeight = copyWithDecrementedWeight.getRevoked();

        assertThat(grantedWithDecrementedWeight).isEqualTo(grantedMap.copyWithDecrementedWeight());
        assertThat(revokedWithDecrementedWeight).isEqualTo(revokedMap.copyWithDecrementedWeight());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToOverrideByNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.overrideBy(null))
                .withMessage("The %s must not be null!", "update")
                .withNoCause();
    }

    @Test
    public void overrideByReturnsExpected() {
        final PermissionSubjectsMap updateGrantedMap = new PermissionSubjectsMap();
        updateGrantedMap.put("READ", Collections.singletonMap(anotherSubjectId, 1));
        final PermissionSubjectsMap updateRevokedMap = new PermissionSubjectsMap();
        updateRevokedMap.put("WRITE", Collections.singletonMap(subjectId, 2));
        final GrantRevokeIndex update = new GrantRevokeIndex(updateGrantedMap, updateRevokedMap);
        final PermissionSubjectsMap expectedGrantedMap = grantedMap.copy();
        expectedGrantedMap.put("WRITE", Collections.emptyMap());
        final PermissionSubjectsMap expectedRevokedMap = revokedMap.copy();
        expectedRevokedMap.put("READ", Collections.emptyMap());
        expectedRevokedMap.get("WRITE").put(subjectId, 2);

        final GrantRevokeIndex overwritten = underTest.overrideBy(update);

        assertThat(overwritten.getGranted()).isEqualTo(expectedGrantedMap);
        assertThat(overwritten.getRevoked()).isEqualTo(expectedRevokedMap);
    }

    @Test
    public void subjectIdHasReadAndWritePermissions() {
        final boolean hasPermissions = underTest.hasPermissions(Collections.singleton(subjectId), permissions);

        assertThat(hasPermissions).isTrue();
    }

    @Test
    public void anotherSubjectIdHasNoWritePermission() {
        final boolean hasPermissions = underTest.hasPermissions(Collections.singleton(anotherSubjectId),
                Collections.singleton("WRITE"));

        assertThat(hasPermissions).isFalse();
    }

    /**
     *
     */
    @Test
    public void anotherSubjectIdHasNoReadPermissionBecauseRevokedWithSameWeight() {
        final boolean hasPermissions = underTest.hasPermissions(Collections.singleton(anotherSubjectId),
                Collections.singleton("READ"));

        assertThat(hasPermissions).isFalse();
    }

    @Test
    public void getEffectedSubjectsForReadPermissionReturnsExpected() {
        final EffectedSubjects effectedSubjects = underTest.getEffectedSubjects(Collections.singleton("READ"));
        final Set<AuthorizationSubject> grantedSubjects = effectedSubjects.getGranted();
        final Set<AuthorizationSubject> revokedSubjects = effectedSubjects.getRevoked();

        assertThat(grantedSubjects).as("Granted subjects")
                .containsOnly(AuthorizationSubject.newInstance(subjectId),
                        AuthorizationSubject.newInstance(anotherSubjectId));
        assertThat(revokedSubjects).as("Revoked subjects")
                .containsOnly(AuthorizationSubject.newInstance(anotherSubjectId));
    }

    @Test
    public void getEffectedSubjectIdsForWritePermissionReturnsExpected() {
        final EffectedSubjects effectedSubjects =
                underTest.getEffectedSubjects(Collections.singleton("WRITE"));
        final Set<AuthorizationSubject> grantedSubjectIds = effectedSubjects.getGranted();
        final Set<AuthorizationSubject> revokedSubjectIds = effectedSubjects.getRevoked();

        assertThat(grantedSubjectIds).as("Granted subject IDs")
                .containsOnly(AuthorizationSubject.newInstance(subjectId));
        assertThat(revokedSubjectIds).as("Revoked subject IDs")
                .containsOnly(AuthorizationSubject.newInstance(anotherSubjectId));
    }

}
