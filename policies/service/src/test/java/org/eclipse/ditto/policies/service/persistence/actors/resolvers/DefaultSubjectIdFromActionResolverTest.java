/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.resolvers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIdInvalidException;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.resolvers.DefaultSubjectIdFromActionResolver}.
 */
public final class DefaultSubjectIdFromActionResolverTest {

    private static final Label LABEL = Label.of("label");
    private static final PolicyEntry ENTRY = PoliciesModelFactory.newPolicyEntry(LABEL, """
            {
              "subjects": {
                "abc:def": {
                  "type": "def"
                }
              },
              "resources": {
                "policy:/": {
                  "grant": ["READ"],
                  "revoke": ["WRITE"]
                }
              }
            }""");

    @Test
    public void resolveSubjectWithoutPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:hello");
        assertThat(DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, Collections.singleton(subjectId)))
                .isEqualTo(Collections.singleton(subjectId));
    }

    @Test
    public void resolveSubjectWithPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{policy-entry:label}}");
        assertThat(DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, Collections.singleton(subjectId)))
                .isEqualTo(Collections.singleton(SubjectId.newInstance("integration:label")));
    }

    @Test
    public void resolveSubjectWithDeletedPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{ policy-entry:label | fn:delete() }}subject");
        assertThat(DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, Collections.singleton(subjectId)))
                .isEqualTo(Collections.singleton(SubjectId.newInstance("integration:subject")));
    }

    @Test
    public void doNotResolveIfSubjectIsEmpty() {
        final Collection<SubjectId> subjectIds = List.of(SubjectId.newInstance("integration:{{fn:delete()}}"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, subjectIds));
    }

    @Test
    public void doNotResolveSubjectWithSupportedAndUnresolvedPlaceholderFix() {
        final Collection<SubjectId> subjectIds = List.of( SubjectId.newInstance("{{fn:delete()}}"));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, subjectIds));
    }

    @Test
    public void throwErrorOnUnsupportedPlaceholder() {
        final Collection<SubjectId> subjectIds = List.of( SubjectId.newInstance("integration:{{connection:id}}"));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, subjectIds));
    }

    @Test
    public void throwSubjectIdInvalidException() {
        final Collection<SubjectId> subjectIds = List.of(SubjectId.newInstance("{{policy-entry:label}}"));
        assertThatExceptionOfType(SubjectIdInvalidException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, subjectIds));
    }
}
