/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.junit.Test;

public final class AbstractTypedSubstitutionStrategyTest {

    @Test
    public void substitutePolicyEntryPreservesNamespacesWhenSubjectsChange() {
        final PolicyEntry existingPolicyEntry = PoliciesModelFactory.newPolicyEntry("SCOPED",
                Collections.singletonList(Subject.newInstance(SubjectId.newInstance("{{ request:subjectId }}"))),
                Collections.singletonList(PoliciesModelFactory.newResource("thing", "/",
                        EffectedPermissions.newInstance(
                                PoliciesModelFactory.newPermissions("READ"),
                                PoliciesModelFactory.noPermissions()))),
                ImportableType.IMPLICIT,
                Collections.emptySet(),
                Arrays.asList("com.acme", "com.acme.*"));
        final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm =
                HeaderBasedPlaceholderSubstitutionAlgorithm.newInstance(replacementDefinitions());

        final PolicyEntry substitutedPolicyEntry = AbstractTypedSubstitutionStrategy.substitutePolicyEntry(
                existingPolicyEntry, substitutionAlgorithm, DittoHeaders.empty());

        assertThat(substitutedPolicyEntry.getNamespaces()).containsExactly("com.acme", "com.acme.*");
        assertThat(substitutedPolicyEntry.getSubjects().stream()
                .map(subject -> subject.getId().toString()))
                .containsExactly("test:bob");
    }

    private static Map<String, Function<DittoHeaders, String>> replacementDefinitions() {
        final Map<String, Function<DittoHeaders, String>> replacementDefinitions = new LinkedHashMap<>();
        replacementDefinitions.put(SubjectIdReplacementDefinition.REPLACER_NAME, dittoHeaders -> "test:bob");
        return replacementDefinitions;
    }

}
