/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario2;

import java.util.function.Function;

import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;


public interface Scenario2Nested extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario2Nested.class.getSimpleName();

    String SUBJECT_ATTRIBUTES_ALL_GRANTED = "sid_attributes_all";
    String SUBJECT_FEATURES_READ_GRANTED = "sid_features_read";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder("benchmark:" + Scenario2Nested1.class.getSimpleName()) //
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
