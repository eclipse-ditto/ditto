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
public class Scenario3Revoke5 implements Scenario3Revoke {

    private static final AuthorizationSubject EXPECTED_GRANTED_SUBJECT = AuthorizationSubject.newInstance(
            SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED));

    private final ScenarioSetup setup;

    public Scenario3Revoke5() {
        final String resource = "/attributes/location";
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ+WRITE granted on '/'. Subject has READ+WRITE revoked on '/attributes'." +
                        " Subject has READ granted on '" + resource + "'. Is able to READ '" + resource + "'.",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED),
                resource,
                Collections.singleton(EXPECTED_GRANTED_SUBJECT),
                policyAlgorithm -> // as the subject has READ granted on "/attributes/location" he shall be able to read "/attributes" partially
                        policyAlgorithm.getSubjectsWithPartialPermission(
                                PoliciesResourceType.thingResource("/attributes"), Permissions.newInstance("READ"))
                                .contains(EXPECTED_GRANTED_SUBJECT),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
