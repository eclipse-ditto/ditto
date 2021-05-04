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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


public interface Scenario2Nested extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario2Nested.class.getSimpleName();

    String SUBJECT_ATTRIBUTES_ALL_GRANTED = "sid_attributes_all";
    String SUBJECT_FEATURES_READ_GRANTED = "sid_features_read";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", Scenario2Nested1.class.getSimpleName())) //
            .forLabel("attributes") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ATTRIBUTES_ALL_GRANTED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .forLabel("features") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features"), "READ") //
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
