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
package org.eclipse.ditto.policies.model.signals.commands;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.PolicyImports;

/**
 * Validator for policy imports.
 */
public final class PolicyImportsValidator {

    private PolicyImportsValidator() {
    }

    public static PolicyImports validatePolicyImports(@Nullable final PolicyId importingPolicyId, final PolicyImports policyImports) {
        if (policyImports.stream().anyMatch(policyImport -> policyImport.getImportedPolicyId().equals(importingPolicyId))) {
            throw PolicyImportInvalidException.newBuilder().build();
        }
        // Validate that no transitiveImports entry references the importing policy itself (cycle prevention)
        policyImports.stream()
                .flatMap(policyImport -> policyImport.getTransitiveImports().stream())
                .filter(transitiveId -> transitiveId.equals(importingPolicyId))
                .findAny()
                .ifPresent(selfRef -> {
                    throw PolicyImportInvalidException.newBuilder()
                            .message("The policy import contains a transitive resolution of the policy itself.")
                            .description("The 'transitiveImports' array must not contain the importing policy's " +
                                    "own ID '" + importingPolicyId + "'.")
                            .build();
                });
        return policyImports;
    }

    public static PolicyImport validatePolicyImport(final PolicyId importingPolicyId, final PolicyImport policyImport) {
        if (policyImport.getImportedPolicyId().equals(importingPolicyId)) {
            throw PolicyImportInvalidException.newBuilder().build();
        }
        // Validate that no transitiveImports entry references the importing policy itself (cycle prevention)
        policyImport.getTransitiveImports().stream()
                .filter(transitiveId -> transitiveId.equals(importingPolicyId))
                .findAny()
                .ifPresent(selfRef -> {
                    throw PolicyImportInvalidException.newBuilder()
                            .message("The policy import contains a transitive resolution of the policy itself.")
                            .description("The 'transitiveImports' array must not contain the importing policy's " +
                                    "own ID '" + importingPolicyId + "'.")
                            .build();
                });
        return policyImport;
    }

    /**
     * Validates that the given {@code transitiveImports} list does not introduce a cycle by referencing the
     * importing policy itself.
     *
     * @param importingPolicyId the ID of the policy that declares the import.
     * @param importedPolicyId the ID of the directly imported policy.
     * @param transitiveImports the list of policy IDs to resolve transitively.
     * @return the validated {@code transitiveImports} list.
     * @throws PolicyImportInvalidException if the importing policy's own ID appears in {@code transitiveImports}.
     * @since 3.9.0
     */
    public static List<PolicyId> validateTransitiveImports(final PolicyId importingPolicyId,
            final PolicyId importedPolicyId,
            final List<PolicyId> transitiveImports) {
        if (importedPolicyId.equals(importingPolicyId)) {
            throw PolicyImportInvalidException.newBuilder().build();
        }
        transitiveImports.stream()
                .filter(transitiveId -> transitiveId.equals(importingPolicyId))
                .findAny()
                .ifPresent(selfRef -> {
                    throw PolicyImportInvalidException.newBuilder()
                            .message("The policy import contains a transitive resolution of the policy itself.")
                            .description("The 'transitiveImports' array must not contain the importing policy's " +
                                    "own ID '" + importingPolicyId + "'.")
                            .build();
                });
        return transitiveImports;
    }

}
