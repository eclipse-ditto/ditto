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

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario2Nested3 implements Scenario2Nested {

    private static final AuthorizationSubject EXPECTED_GRANTED_SUBJECT =
            AuthorizationSubject.newInstance(
                    SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ATTRIBUTES_ALL_GRANTED).toString());

    private final ScenarioSetup setup;

    public Scenario2Nested3() {
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ+WRITE granted on '/attributes'. Is able to WRITE '/attributes'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ATTRIBUTES_ALL_GRANTED),
                "/attributes",
                Collections.singleton(EXPECTED_GRANTED_SUBJECT),
                policyAlgorithm -> {
                    final Set<AuthorizationSubject> sids = policyAlgorithm.getSubjectsWithPartialPermission(
                            PoliciesResourceType.thingResource("/attributes"),
                            Permissions.newInstance("READ", "WRITE"));
                    return 1 == sids.size() &&
                            sids.contains(EXPECTED_GRANTED_SUBJECT);
                },
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
