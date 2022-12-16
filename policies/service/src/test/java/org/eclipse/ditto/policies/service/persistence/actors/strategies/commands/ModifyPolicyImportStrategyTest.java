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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportModified;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ModifyPolicyImportStrategy}.
 */
public final class ModifyPolicyImportStrategyTest extends AbstractPolicyCommandStrategyTest {

    private ModifyPolicyImportStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyPolicyImportStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyImportStrategy.class, areImmutable());
    }

    @Test
    public void createPolicyImport() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final PolicyImport policyImport = TestConstants.Policy.policyImportWithId("newImport");

        final ModifyPolicyImport command = ModifyPolicyImport.of(TestConstants.Policy.POLICY_ID, policyImport,
                dittoHeaders);

        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyImportCreated.class,
                event -> {
                    assertThat((CharSequence) event.getPolicyImport().getImportedPolicyId())
                            .isEqualTo(policyImport.getImportedPolicyId());
                },
                ModifyPolicyImportResponse.class,
                response -> {
                }
        );
    }

    @Test
    public void modifyPolicyImport() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final Policy policyWithImport = TestConstants.Policy.POLICY;

        final PolicyImport policyImport = TestConstants.Policy.POLICY_IMPORT_WITH_ENTRIES;

        final ModifyPolicyImport command = ModifyPolicyImport.of(TestConstants.Policy.POLICY_ID, policyImport,
                dittoHeaders);

        assertModificationResult(underTest, policyWithImport, command,
                PolicyImportModified.class,
                event -> {
                    assertThat((CharSequence) event.getPolicyImport().getImportedPolicyId())
                            .isEqualTo(policyImport.getImportedPolicyId());
                },
                ModifyPolicyImportResponse.class,
                response -> {
                }
        );
    }
}
