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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario7;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;

/**
 * Checks of partial permissions where a permission is granted and revoked on the same sub resource, either for the
 * same subject or for different subjects of the authorization context.
 */
public interface Scenario7PartialPermissions extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario7PartialPermissions.class.getSimpleName();

    String SUBJECT_USER = "uid_user";
    String SUBJECT_RESTRICTED_GROUP = "gid_restricted";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", Scenario7PartialPermissions.class.getSimpleName())) //
            .forLabel("user") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_USER) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/secret"), "READ") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/broken/properties/on"), "READ") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/fixed/properties/on"), "READ") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/fixed/properties/on/value"),
                    "READ") //
            .forLabel("userRevoked") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_USER) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/broken/properties/on"), "READ") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/fixed/properties/on"), "READ") //
            .forLabel("restricted") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_RESTRICTED_GROUP) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/secret"), "READ") //
            .build();

    @Override
    default Policy getPolicy() {
        return POLICY;
    }

    @Override
    default String getScenarioGroup() {
        return SCENARIO_GROUP_NAME;
    }

    @Override
    default Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction() {
        return algorithm -> algorithm.hasPermissionsOnResourceOrAnySubresource(getSetup());
    }

}
