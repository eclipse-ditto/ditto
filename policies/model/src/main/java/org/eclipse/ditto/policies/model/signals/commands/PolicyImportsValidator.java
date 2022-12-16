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
        return policyImports;
    }

    public static PolicyImport validatePolicyImport(final PolicyId importingPolicyId, final PolicyImport policyImport) {
        if (policyImport.getImportedPolicyId().equals(importingPolicyId)) {
            throw PolicyImportInvalidException.newBuilder().build();
        }
        return policyImport;
    }

}
