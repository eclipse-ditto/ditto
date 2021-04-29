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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2;

import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario2Nested11 implements Scenario2Nested {

    private final ScenarioSetup setup;

    public Scenario2Nested11() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ granted on '/features'. Is able to READ '/features/foo'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_FEATURES_READ_GRANTED), //
                "/features/foo", //
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
