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

import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
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
                policyAlgorithm -> policyAlgorithm.getSubjectsWithPartialPermission(
                        PoliciesResourceType.thingResource("/"), Permissions.newInstance("READ"))
                        .containsAll(Stream.of(
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ATTRIBUTES_ALL_GRANTED),
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED)
                                ).map(AuthorizationSubject::newInstance).collect(Collectors.toList())
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
