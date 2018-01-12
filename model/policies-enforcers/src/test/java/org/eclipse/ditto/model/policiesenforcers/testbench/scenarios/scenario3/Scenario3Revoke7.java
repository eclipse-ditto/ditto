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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario3;

import java.util.Collections;
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
                        policyAlgorithm.getSubjectIdsWithPartialPermission(
                                PoliciesResourceType.thingResource("/features"), "READ")
                                .contains(EXPECTED_GRANTED_SUBJECT),
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
