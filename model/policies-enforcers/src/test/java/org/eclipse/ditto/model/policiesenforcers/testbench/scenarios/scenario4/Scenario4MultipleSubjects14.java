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

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario4MultipleSubjects14 implements Scenario4MultipleSubjects {

    private final ScenarioSetup setup;

    public Scenario4MultipleSubjects14() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject_5 has READ granted on '/features/public/properties/location'. "
                        + "Subject_5 has WRITE granted on '/features/lamp/properties/config/on'. "
                        + "Is able to READ '/features/public' with hasPermissionsOnResourceOrAnySubresource()", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_5), //
                "/features/public", //
                Stream.of(
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_1).toString(),
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_2).toString(),
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_3).toString())
                        .collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

    @Override
    public Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction() {
        // algorithm invoked with hasPermissionsOnResourceOrAnySubresource! as we would like to know if the subject can read anywhere
        // in the hierarchy below the passed path:
        return algorithm -> algorithm.hasPermissionsOnResourceOrAnySubresource(getSetup());
    }
}
