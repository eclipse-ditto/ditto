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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario5;

import java.util.function.Function;

import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;


public interface Scenario5Simple extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario5Simple1.class.getSimpleName();

    String SUBJECT = "sid";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder("benchmark:" + Scenario5Simple1.class.getSimpleName()) //
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
