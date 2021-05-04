
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
public class Scenario4MultipleSubjects27 implements Scenario4MultipleSubjects {

    private final ScenarioSetup setup;

    public Scenario4MultipleSubjects27() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "SUBJECT_4 has READ granted on '/attributes/nogo2/go2'. " //
                        + "Subject_9 has READ revoked on '/attributes/nogo2'. " //
                        + "Subject_9 has WRITE granted on '/attributes/nogo2/go2'. " //
                        + "Subject_4 has WRITE revoked on '/attributes/nogo2'. " //
                        + "Is able to READ+WRITE '/attributes/nogo2/go2'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_4, SUBJECT_9), //
                "/attributes/nogo1/go1", //
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
