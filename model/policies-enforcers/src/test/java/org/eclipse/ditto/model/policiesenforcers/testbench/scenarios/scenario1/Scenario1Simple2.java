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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario1;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario1Simple2 implements Scenario1Simple {

    private final ScenarioSetup setup;

    public Scenario1Simple2() {
        setup = Scenario.newScenarioSetup( //
                false, //
                "Subject is not present. Is NOT able to READ and WRITE '/'", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_NONE_GRANTED), //
                "/", //
                Stream.of(
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED).toString(),
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_WRITE_REVOKED).toString())
                        .collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
