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

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario2Nested3 implements Scenario2Nested {

    private static final String EXPECTED_GRANTED_SUBJECT =
            SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ATTRIBUTES_ALL_GRANTED).toString();

    private final ScenarioSetup setup;

    public Scenario2Nested3() {
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ+WRITE granted on '/attributes'. Is able to WRITE '/attributes'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ATTRIBUTES_ALL_GRANTED),
                "/attributes",
                Collections.singleton(EXPECTED_GRANTED_SUBJECT),
                policyAlgorithm -> {
                    final Set<String> sids = policyAlgorithm.getSubjectIdsWithPartialPermission(
                            PoliciesResourceType.thingResource("/attributes"), "READ", "WRITE");
                    return 1 == sids.size() && sids.contains(EXPECTED_GRANTED_SUBJECT);
                },
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
