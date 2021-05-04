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
import java.util.function.Function;

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
public class Scenario3Revoke7 implements Scenario3Revoke {

    private static final String EXPECTED_GRANTED_SUBJECT = SubjectId.newInstance(SubjectIssuer.GOOGLE,
            SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED).toString();

    private final ScenarioSetup setup;

    public Scenario3Revoke7() {
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ granted on '/features'. Subject has READ revoked on '/features/firmware'." +
                        " Is able to READ '/' with hasPermissionsOnResourceOrAnySubresource().",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED),
                "/",
                Collections.emptySet(),
                policyAlgorithm -> // as the subject has READ granted on "/features" he shall be able to read "/" partially
                        policyAlgorithm.getSubjectsWithPartialPermission(
                                PoliciesResourceType.thingResource("/features"), Permissions.newInstance("READ"))
                                .contains(AuthorizationSubject.newInstance(EXPECTED_GRANTED_SUBJECT)),
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
