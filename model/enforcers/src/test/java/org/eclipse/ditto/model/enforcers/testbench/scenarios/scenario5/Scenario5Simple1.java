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
package org.eclipse.ditto.model.enforcers.testbench.scenarios.scenario5;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario5Simple1 implements Scenario5Simple {

    private final ScenarioSetup setup;

    public Scenario5Simple1() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ granted on '/'. Is allowed to READ '/policy'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT), //
                "/policy", //
                Stream.of(SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT).toString())
                        .collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
