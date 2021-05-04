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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4;

import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario4MultipleSubjects26 implements Scenario4MultipleSubjects {

    private final ScenarioSetup setup;

    public Scenario4MultipleSubjects26() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "SUBJECT_8 has WRITE granted on '/attributes'. " //
                        + "Subject_4 has WRITE revoked on '/attributes/nogo1' and '/attributes/nogo2'. " //
                        + "Subject_4 has WRITE granted on '/attributes/nogo1/go1'. " //
                        + "Is able to WRITE '/attributes/nogo1/go1'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_8, SUBJECT_4), //
                "/attributes/nogo1/go1", //
                "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
