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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario5;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
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
