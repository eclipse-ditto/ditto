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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario4;

import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
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
