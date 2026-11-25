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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario6;

import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class Scenario6MultiplePermissions7 implements Scenario6MultiplePermissions {

    private final ScenarioSetup setup;

    public Scenario6MultiplePermissions7() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "User has WRITE granted on '/'. Reader group has READ granted on '/features'. User + reader group are able to READ+WRITE '/features/lamp'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_USER, SUBJECT_READER_GROUP), //
                "/features/lamp", //
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
