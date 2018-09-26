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
package org.eclipse.ditto.model.enforcers.testbench.scenarios.scenario4;

import java.util.function.Function;

import org.eclipse.ditto.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario4MultipleSubjects22 implements Scenario4MultipleSubjects {

    private final ScenarioSetup setup;

    public Scenario4MultipleSubjects22() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "SUBJECT_8 has READ granted on '/attributes'. " //
                        + "Subject_4 has READ revoked on '/attributes/nogo1' and '/attributes/nogo2'. " //
                        + "Subject_4 has READ granted on '/attributes/nogo1/go1' and '/attributes/nogo2/go2'. " //
                        + "Is able to READ '/attributes' with hasPermissionsOnResourceOrAnySubresource()", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_8, SUBJECT_4), //
                "/attributes", //
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

    @Override
    public Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction() {
        // algorithm invoked with hasPermissionsOnResourceOrAnySubresource! as we would like to know if the subject can read anywhere
        // in the hierarchy below the passed path:
        return algorithm -> algorithm.hasPermissionsOnResourceOrAnySubresource(getSetup());
    }
}
