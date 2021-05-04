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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario5;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


public interface Scenario5Simple extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario5Simple1.class.getSimpleName();

    String SUBJECT = "sid";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", Scenario5Simple1.class.getSimpleName())) //
            .forLabel("all") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ") //
            .forLabel("revokeWriteOnPolicy") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/policy"), "WRITE") //
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
        return algorithm -> algorithm.hasPermissionsOnResourceOrAnySubresource(getSetup());
    }
}
