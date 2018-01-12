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

import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario2Nested7 implements Scenario2Nested {

    private final ScenarioSetup setup;

    public Scenario2Nested7() {
        setup = Scenario.newScenarioSetup( //
                false, //
                "Subject has READ granted on '/features'. Is NOT able to READ '/attributes'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_FEATURES_READ_GRANTED), //
                "/attributes", //
                Collections.emptySet(),
                policyAlgorithm ->
                        !policyAlgorithm.getSubjectIdsWithPartialPermission(
                                PoliciesResourceType.thingResource("/attributes"), "READ")
                                .contains(SubjectId
                                        .newInstance(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED)
                                        .toString()
                                ),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
