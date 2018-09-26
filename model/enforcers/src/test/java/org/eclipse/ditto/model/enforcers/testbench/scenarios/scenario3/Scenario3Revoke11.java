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
public class Scenario3Revoke11 implements Scenario3Revoke {

    private final ScenarioSetup setup;

    public Scenario3Revoke11() {
        setup = Scenario.newScenarioSetup( //
                false, // user may NOT read "/features/firmware" (forbidden by revoke)
                "Subject has READ granted on '/features'. Subject has READ revoked on '/features/firmware'. Is NOT able to READ '/features/firmware'",
                //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED), //
                "/features/firmware", //
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
