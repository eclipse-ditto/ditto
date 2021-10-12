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

import java.util.Collections;

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
    private static final PolicyEntry ENTRY = PoliciesModelFactory.newPolicyEntry(LABEL, "{\n" +
            "  \"subjects\": {\n" +
            "    \"abc:def\": {\n" +
            "      \"type\": \"def\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"resources\": {\n" +
            "    \"policy:/\": {\n" +
            "      \"grant\": [\"READ\"],\n" +
            "      \"revoke\": [\"WRITE\"]\n" +
            "    }\n" +
            "  }\n" +
            "}");

    private static final DefaultSubjectIdFromActionResolver sut = new DefaultSubjectIdFromActionResolver();

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
    public void doNotResolveSubjectWithSupportedAndUnresolvedPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{fn:delete()}}");
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, Collections.singleton(subjectId)));
    }

    @Test
    public void throwErrorOnUnsupportedPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{connection:id}}");
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, Collections.singleton(subjectId)));
    }

    @Test
    public void throwSubjectIdInvalidException() {
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        assertThatExceptionOfType(SubjectIdInvalidException.class)
                .isThrownBy(() -> DefaultSubjectIdFromActionResolver.resolveSubjectId(ENTRY, Collections.singleton(subjectId)));
    }
}
