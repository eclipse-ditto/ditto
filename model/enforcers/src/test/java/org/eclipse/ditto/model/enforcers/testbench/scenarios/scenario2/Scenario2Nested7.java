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
package org.eclipse.ditto.model.enforcers.testbench.scenarios.scenario2;

import java.util.Collections;

import org.eclipse.ditto.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
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
