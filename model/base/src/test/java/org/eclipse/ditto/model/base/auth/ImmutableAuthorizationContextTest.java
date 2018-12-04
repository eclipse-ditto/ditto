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
package org.eclipse.ditto.model.base.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAuthorizationContext}.
 */
public final class ImmutableAuthorizationContextTest {

    private static final List<AuthorizationSubject> SUBJECTS_1 =
            Arrays.asList(AuthorizationSubject.newInstance("sub_1_1"), AuthorizationSubject.newInstance("sub_1_2"));

    private static final List<AuthorizationSubject> SUBJECTS_2 =
            Arrays.asList(AuthorizationSubject.newInstance("sub_2_1"), AuthorizationSubject.newInstance("sub_2_2"));

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAuthorizationContext.class,
                areImmutable(),
                assumingFields("authorizationSubjects").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAuthorizationContext.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void addHead() {
        final AuthorizationContext initialContext = ImmutableAuthorizationContext.of(SUBJECTS_1);

        final AuthorizationContext newContext = initialContext.addHead(SUBJECTS_2);

        final List<AuthorizationSubject> expectedAuthSubjects = new ArrayList<>();
        expectedAuthSubjects.addAll(SUBJECTS_2);
        expectedAuthSubjects.addAll(SUBJECTS_1);
        final AuthorizationContext expectedContext = ImmutableAuthorizationContext.of(expectedAuthSubjects);

        assertThat(newContext).isEqualTo(expectedContext);
    }

    @Test
    public void addTail() {
        final AuthorizationContext initialContext = ImmutableAuthorizationContext.of(SUBJECTS_1);

        final AuthorizationContext newContext = initialContext.addTail(SUBJECTS_2);

        final List<AuthorizationSubject> expectedAuthSubjects = new ArrayList<>();
        expectedAuthSubjects.addAll(SUBJECTS_1);
        expectedAuthSubjects.addAll(SUBJECTS_2);
        final AuthorizationContext expectedContext = ImmutableAuthorizationContext.of(expectedAuthSubjects);

        assertThat(newContext).isEqualTo(expectedContext);
    }
}
