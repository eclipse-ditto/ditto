/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario7;

import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class Scenario7PartialPermissions4 implements Scenario7PartialPermissions {

    private final ScenarioSetup setup;

    public Scenario7PartialPermissions4() {
        setup = Scenario.newScenarioSetup( //
                false, //
                "User has READ granted and revoked on '/features/broken/properties/on'. Is NOT able to READ '/features/broken' or any of its sub-resources", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_USER), //
                "/features/broken", //
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
