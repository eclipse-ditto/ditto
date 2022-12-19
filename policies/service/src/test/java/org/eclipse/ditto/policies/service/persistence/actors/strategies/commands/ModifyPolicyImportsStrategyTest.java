/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.base.model.signals.commands.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportsResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportsModified;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ModifyPolicyImportsStrategy}.
 */
public final class ModifyPolicyImportsStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ModifyPolicyImportsStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyPolicyImportsStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyImportsStrategy.class, areImmutable());
    }

    @Test
    public void modifyPolicyImports() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final Policy policy = TestConstants.Policy.POLICY
                .toBuilder()
                .setRevision(NEXT_REVISION)
                .build();

        final PolicyImports policyImports = TestConstants.Policy.POLICY_IMPORTS;
        final ModifyPolicyImports command = ModifyPolicyImports.of(TestConstants.Policy.POLICY_ID,
                policyImports, dittoHeaders);

        assertModificationResult(underTest, policy, command,
                PolicyImportsModified.class,
                event -> {
                    assertThat(event.getPolicyImports())
                            .containsAll(TestConstants.Policy.POLICY_IMPORTS);
                },
                ModifyPolicyImportsResponse.class,
                response -> {
                }
        );
    }
}
