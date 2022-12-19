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
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportDeleted;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DeletePolicyImportStrategy}.
 */
public final class DeletePolicyImportStrategyTest extends AbstractPolicyCommandStrategyTest {

    private DeletePolicyImportStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeletePolicyImportStrategy(DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeletePolicyImportStrategy.class, areImmutable());
    }

    @Test
    public void deletePolicyImportSuccessful() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();

        final PolicyId importedPolicyId = TestConstants.Policy.POLICY_IMPORT_ID;
        final DeletePolicyImport command = DeletePolicyImport.of(TestConstants.Policy.POLICY_ID, importedPolicyId,
                dittoHeaders);

        assertModificationResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyImportDeleted.class,
                event -> {
                    assertThat((CharSequence) event.getImportedPolicyId()).isEqualTo(importedPolicyId);
                },
                DeletePolicyImportResponse.class,
                response -> {
                    assertThat((CharSequence) response.getImportedPolicyId()).isEqualTo(importedPolicyId);
                }
        );
    }

    @Test
    public void deletePolicyImportNotFound() {
        final DittoHeaders dittoHeaders = DittoHeaders.empty();

        final PolicyId importedPolicyId = PolicyId.of("com.example", "not.found");
        final DeletePolicyImport command = DeletePolicyImport.of(TestConstants.Policy.POLICY_ID, importedPolicyId,
                dittoHeaders);

        assertErrorResult(underTest, TestConstants.Policy.POLICY, command,
                PolicyImportNotAccessibleException.newBuilder(TestConstants.Policy.POLICY_ID, importedPolicyId)
                        .build());
    }
}
