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

import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportReferenceConflictException;
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

    @Test
    public void modifyPolicyImportRejectsRemovingLabelStillReferencedByEntry() {
        // Build a policy whose driver entry references the imported "driver" entry, with the
        // import filter currently containing both "driver" and "guest". The modification removes
        // "driver" from the filter — that would orphan the existing reference.
        final var importedPolicyId = TestConstants.Policy.POLICY_IMPORT_ID;
        final var policyId = TestConstants.Policy.POLICY_ID;
        final EffectedImports oldFilter = PoliciesModelFactory.newEffectedImportedLabels(
                List.of(Label.of("driver"), Label.of("guest")));
        final EffectedImports newFilter = PoliciesModelFactory.newEffectedImportedLabels(
                List.of(Label.of("guest")));

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setSubjectFor("admin", Subject.newInstance(SubjectIssuer.GOOGLE, "admin"))
                .setGrantedPermissionsFor("admin", "policy", "/", "READ", "WRITE")
                .setSubjectFor("driver", Subject.newInstance(SubjectIssuer.GOOGLE, "driver"))
                .setGrantedPermissionsFor("driver", "thing", "/", "READ")
                .setReferencesFor("driver", List.of(
                        PoliciesModelFactory.newEntryReference(importedPolicyId, Label.of("driver"))))
                .setPolicyImports(PolicyImports.newInstance(
                        PolicyImport.newInstance(importedPolicyId, oldFilter)))
                .build();

        final ModifyPolicyImport command = ModifyPolicyImport.of(policyId,
                PolicyImport.newInstance(importedPolicyId, newFilter), DittoHeaders.empty());

        assertErrorResult(underTest, policy, command,
                PolicyImportReferenceConflictException.newBuilder(policyId, importedPolicyId)
                        .description("Removing label 'driver' from this import's filter would orphan the " +
                                "reference from entry 'driver'.")
                        .build());
    }
}
