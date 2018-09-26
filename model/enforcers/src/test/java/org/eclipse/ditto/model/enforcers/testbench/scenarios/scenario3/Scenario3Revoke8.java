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

import org.eclipse.ditto.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario3Revoke8 implements Scenario3Revoke {

    private final ScenarioSetup setup;

    public Scenario3Revoke8() {
        setup = Scenario.newScenarioSetup( //
                false, //
                "Subject has READ granted on '/features'. Subject has READ revoked on '/features/firmware'. Is NOT able to WRITE '/'",
                //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED), //
                "/", //
                "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
