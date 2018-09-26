/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.enforcers.testbench.scenarios.scenario3;

import java.util.Collections;

import org.eclipse.ditto.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario3Revoke1 implements Scenario3Revoke {

    private static final String EXPECTED_GRANTED_SUBJECT = SubjectId.newInstance(SubjectIssuer.GOOGLE,
            SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED).toString();

    private final ScenarioSetup setup;

    public Scenario3Revoke1() {
        setup = Scenario.newScenarioSetup(
                false,
                "Subject has READ+WRITE granted on '/'. Subject has READ+WRITE revoked on '/attributes'. Subject has READ granted on '/attributes/location'. Is NOT able to READ+WRITE '/'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED),
                "/",
                Collections.emptySet(),
                policyAlgorithm -> policyAlgorithm.getSubjectIdsWithPartialPermission(
                        PoliciesResourceType.thingResource("/"), "READ").contains(EXPECTED_GRANTED_SUBJECT),
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
