/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.junit.Test;

/**
 * Tests {@link PolicyEntryPlaceholder}.
 */
public final class PolicyEntryPlaceholderTest {

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

    @Test
    public void resolveSubjectWithoutPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:hello");
        assertThat(PolicyEntryPlaceholder.resolveSubjectId(ENTRY, subjectId)).isEqualTo(subjectId);
    }

    @Test
    public void resolveSubjectWithPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{policy-entry:label}}");
        assertThat(PolicyEntryPlaceholder.resolveSubjectId(ENTRY, subjectId))
                .isEqualTo(SubjectId.newInstance("integration:label"));
    }

    @Test
    public void doNotResolveSubjectWithSupportedAndUnresolvedPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{fn:delete()}}");
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> PolicyEntryPlaceholder.resolveSubjectId(ENTRY, subjectId));
    }

    @Test
    public void throwErrorOnUnsupportedPlaceholder() {
        final SubjectId subjectId = SubjectId.newInstance("integration:{{connection:id}}");
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> PolicyEntryPlaceholder.resolveSubjectId(ENTRY, subjectId));
    }

    @Test
    public void throwSubjectIdInvalidException() {
        final SubjectId subjectId = SubjectId.newInstance("{{policy-entry:label}}");
        assertThatExceptionOfType(SubjectIdInvalidException.class)
                .isThrownBy(() -> PolicyEntryPlaceholder.resolveSubjectId(ENTRY, subjectId));
    }
}
