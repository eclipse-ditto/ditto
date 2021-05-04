/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


public interface Scenario1Simple extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario1Simple.class.getSimpleName();

    String SUBJECT_ALL_GRANTED = "sid_all";
    String SUBJECT_NONE_GRANTED = "sid_none";
    String SUBJECT_WRITE_REVOKED = "sid_write_revoke";

    Policy POLICY = PoliciesModelFactory
            .newPolicyBuilder(PolicyId.of("benchmark", SCENARIO_GROUP_NAME))
            .forLabel("all")
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED)
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_WRITE_REVOKED)
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ", "WRITE")
            .forLabel("revokeWrite")
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_WRITE_REVOKED)
            .setRevokedPermissions(PoliciesResourceType.thingResource("/"), "WRITE")
            .build();

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
