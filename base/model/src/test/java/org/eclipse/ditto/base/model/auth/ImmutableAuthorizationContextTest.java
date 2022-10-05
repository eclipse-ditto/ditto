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
package org.eclipse.ditto.base.model.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.auth.ImmutableAuthorizationContext}.
 */
public final class ImmutableAuthorizationContextTest {

    private static final DittoAuthorizationContextType KNOWN_TYPE = DittoAuthorizationContextType.UNSPECIFIED;
    private static final AuthorizationSubject SUB_1_1 = AuthorizationSubject.newInstance("sub_1_1");
    private static final AuthorizationSubject SUB_1_2 = AuthorizationSubject.newInstance("sub_1_2");
    private static final List<AuthorizationSubject> SUBJECTS_1 = Lists.newArrayList(SUB_1_1, SUB_1_2);
    private static final List<AuthorizationSubject> SUBJECTS_2 =
            Lists.newArrayList(AuthorizationSubject.newInstance("sub_2_1"),
                    AuthorizationSubject.newInstance("sub_2_2"));

    private static JsonObject knownJsonRepresentation;

    @BeforeClass
    public static void setUpClass() {
        knownJsonRepresentation = JsonObject.newBuilder()
                .set(AuthorizationContext.JsonFields.TYPE, KNOWN_TYPE.toString())
                .set(AuthorizationContext.JsonFields.AUTH_SUBJECTS, JsonArray.newBuilder()
                        .add(SUB_1_1.getId(), SUB_1_2.getId())
                        .build())
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAuthorizationContext.class,
                areImmutable(),
                provided(AuthorizationContextType.class).isAlsoImmutable(),
                assumingFields("authorizationSubjects",
                        "authorizationSubjectIds").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                assumingFields("authorizationSubjectIds").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAuthorizationContext.class)
                .usingGetClass()
                .withIgnoredFields("authorizationSubjectIds")
                .verify();
    }

    @Test
    public void getFirstAuthorizationSubjectReturnsExpectedIfContextIsNotEmpty() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        assertThat(underTest.getFirstAuthorizationSubject()).contains(SUBJECTS_1.get(0));
    }

    @Test
    public void getFirstAuthorizationSubjectReturnsEmptyOptionalIfContextIsEmpty() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, Lists.emptyList());

        assertThat(underTest.getFirstAuthorizationSubject()).isEmpty();
    }

    @Test
    public void getSizeReturnsExpected() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        assertThat(underTest.getSize()).isEqualTo(SUBJECTS_1.size());
    }

    @Test
    public void isEmptyReturnsFalseForNonEmptyContext() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        assertThat(underTest.isEmpty()).isFalse();
    }

    @Test
    public void isEmptyReturnsTrueForEmptyContext() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, Lists.emptyList());

        assertThat(underTest.isEmpty()).isTrue();
    }

    @Test
    public void getAuthorizationSubjectsReturnsExpected() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_2);

        assertThat(underTest.getAuthorizationSubjects()).containsExactlyInAnyOrderElementsOf(SUBJECTS_2);
    }

    @Test
    public void addHead() {
        final ImmutableAuthorizationContext initialContext = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        final AuthorizationContext newContext = initialContext.addHead(SUBJECTS_2);

        final List<AuthorizationSubject> expectedAuthSubjects = Lists.newArrayList(SUBJECTS_2);
        expectedAuthSubjects.addAll(SUBJECTS_1);

        final ImmutableAuthorizationContext expectedContext =
                ImmutableAuthorizationContext.of(KNOWN_TYPE, expectedAuthSubjects);

        assertThat(newContext).isEqualTo(expectedContext);
    }

    @Test
    public void addTail() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        final AuthorizationContext newContext = underTest.addTail(SUBJECTS_2);

        final List<AuthorizationSubject> expectedAuthSubjects = new ArrayList<>();
        expectedAuthSubjects.addAll(SUBJECTS_1);
        expectedAuthSubjects.addAll(SUBJECTS_2);
        final AuthorizationContext expectedContext = ImmutableAuthorizationContext.of(KNOWN_TYPE, expectedAuthSubjects);

        assertThat(newContext).isEqualTo(expectedContext);
    }

    @Test
    public void getAuthorizationSubjectIdsReturnsExpected() {
        final List<AuthorizationSubject> authorizationSubjects = new ArrayList<>(SUBJECTS_1);
        authorizationSubjects.addAll(SUBJECTS_2);
        final List<String> expected = authorizationSubjects.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toList());

        final ImmutableAuthorizationContext underTest =
                ImmutableAuthorizationContext.of(KNOWN_TYPE, authorizationSubjects);

        final List<String> authorizationSubjectIds = underTest.getAuthorizationSubjectIds();

        assertThat(authorizationSubjectIds).containsExactlyElementsOf(expected);
    }

    @Test
    public void tryToCallIsAuthorizedWithNullGranted() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.isAuthorized(null, SUBJECTS_2))
                .withMessage("The granted must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToCallIsAuthorizedWithNullRevoked() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.isAuthorized(SUBJECTS_2, null))
                .withMessage("The revoked must not be null!")
                .withNoCause();
    }

    @Test
    public void isNotAuthorizedForEmptyGrantedAndRevoked() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        final boolean authorized = underTest.isAuthorized(Lists.emptyList(), Lists.emptyList());

        assertThat(authorized).isFalse();
    }

    @Test
    public void isNotAuthorizedForEmptyGranted() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        final boolean authorized = underTest.isAuthorized(Lists.emptyList(), SUBJECTS_2);

        assertThat(authorized).isFalse();
    }

    @Test
    public void isAuthorizedIfSubjectIsGrantedAndRevokedIsEmpty() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUB_1_1);

        final boolean authorized = underTest.isAuthorized(SUBJECTS_1, Lists.emptyList());

        assertThat(authorized).isTrue();
    }

    @Test
    public void isNotAuthorizedIfSubjectIsGrantedAndRevoked() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUB_1_1);

        final boolean authorized = underTest.isAuthorized(SUBJECTS_1, Lists.newArrayList(SUB_1_1));

        assertThat(authorized).isFalse();
    }

    @Test
    public void isAuthorizedIfSubjectIsGrantedAndRevokedIsDisjoint() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUB_1_1);

        final boolean authorized = underTest.isAuthorized(SUBJECTS_1, SUBJECTS_2);

        assertThat(authorized).isTrue();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ImmutableAuthorizationContext underTest = ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1);

        assertThat(underTest.toJson(FieldType.regularOrSpecial())).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ImmutableAuthorizationContext authorizationContext =
                ImmutableAuthorizationContext.fromJson(knownJsonRepresentation);

        assertThat(authorizationContext).isEqualTo(ImmutableAuthorizationContext.of(KNOWN_TYPE, SUBJECTS_1));
    }

}
