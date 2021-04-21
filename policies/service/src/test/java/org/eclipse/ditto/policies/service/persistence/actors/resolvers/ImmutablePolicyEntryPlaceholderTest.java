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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.resolvers.ImmutablePolicyEntryPlaceholder}.
 */
public class ImmutablePolicyEntryPlaceholderTest {

    private static final Label LABEL = Label.of("some-label");
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
    public void testImmutability() {
        assertInstancesOf(ImmutablePolicyEntryPlaceholder.class, areImmutable());
    }

    @Test
    public void testReplaceTopic() {
        assertThat(ImmutablePolicyEntryPlaceholder.INSTANCE.resolve(ENTRY, "label")).contains(LABEL.toString());
    }

    @Test
    public void testResultIsEmptyForUnknownPlaceholder() {
        assertThat(ImmutablePolicyEntryPlaceholder.INSTANCE.resolve(ENTRY, "invalid")).isEmpty();
    }
}
