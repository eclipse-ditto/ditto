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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3;

import java.util.Collections;

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
public class Scenario3Revoke1 implements Scenario3Revoke {

    private static final String EXPECTED_GRANTED_SUBJECT = SubjectId.newInstance(SubjectIssuer.GOOGLE,
            SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED).toString();

    private final ScenarioSetup setup;

    public Scenario3Revoke1() {
        setup = Scenario.newScenarioSetup(
                false,
                "Subject has READ+WRITE granted on '/'. Subject has READ+WRITE revoked on '/attributes'. Subject has READ granted on '/attributes/location'. Is NOT able to READ+WRITE '/'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED),
                "/",
                Collections.emptySet(),
                policyAlgorithm -> policyAlgorithm.getSubjectsWithPartialPermission(
                        PoliciesResourceType.thingResource("/"), Permissions.newInstance("READ"))
                        .contains(AuthorizationSubject.newInstance(EXPECTED_GRANTED_SUBJECT)),
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
