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
public class Scenario3Revoke18 implements Scenario3Revoke {

    private static final AuthorizationSubject EXPECTED_GRANTED_SUBJECT = AuthorizationSubject.newInstance(
            SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED));

    private final ScenarioSetup setup;

    public Scenario3Revoke18() {
        final String resource = "/features/foo/properties/some";
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ+WRITE granted on '/features/foo'. Subject has READ+WRITE revoked on " +
                        "'/features/foo/properties/special'. Is able to WRITE '" + resource + "'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED),
                resource,
                Collections.singleton(EXPECTED_GRANTED_SUBJECT),
                policyAlgorithm -> // the subject shall be able to write "/features/foo/properties/some" partially
                        policyAlgorithm.getSubjectsWithPartialPermission(
                                PoliciesResourceType.thingResource(resource), Permissions.newInstance("WRITE"))
                                .contains(EXPECTED_GRANTED_SUBJECT),
                "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
