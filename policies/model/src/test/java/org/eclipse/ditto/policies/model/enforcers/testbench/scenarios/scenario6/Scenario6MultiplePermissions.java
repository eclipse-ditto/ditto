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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario6;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;

/**
 * Checks of several permissions at once for an authorization context with several subjects, where the grant and the
 * revoke of a permission belong to different subjects.
 */
public interface Scenario6MultiplePermissions extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario6MultiplePermissions.class.getSimpleName();

    String SUBJECT_USER = "uid_alice";
    String SUBJECT_RESTRICTED_GROUP = "gid_restricted";
    String SUBJECT_READER_GROUP = "gid_reader";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", Scenario6MultiplePermissions.class.getSimpleName())) //
            .forLabel("user") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_USER) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "WRITE") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/pub"), "READ") //
            .forLabel("restricted") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_RESTRICTED_GROUP) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes"), "WRITE") //
            .forLabel("reader") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_READER_GROUP) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features"), "READ") //
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
        return algorithm -> algorithm.hasPermissionsOnResource(getSetup());
    }

}
