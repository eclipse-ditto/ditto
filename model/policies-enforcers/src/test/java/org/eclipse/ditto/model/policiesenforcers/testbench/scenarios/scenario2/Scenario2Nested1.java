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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Function;

import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario2Nested1 implements Scenario2Nested {

    private final ScenarioSetup setup;

    public Scenario2Nested1() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ+WRITE granted on '/attributes'. Is able to READ '/' with hasPermissionsOnResourceOrAnySubresource()",
                //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_ATTRIBUTES_ALL_GRANTED), //
                "/", //
                new HashSet<>(),
                policyAlgorithm -> policyAlgorithm.getSubjectIdsWithPartialPermission(
                        PoliciesResourceType.thingResource("/"), "READ")
                        .containsAll(Arrays.asList(
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ATTRIBUTES_ALL_GRANTED)
                                        .toString(),
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED)
                                        .toString()
                                )
                        ),
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
