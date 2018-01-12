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

import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario3Revoke18 implements Scenario3Revoke {

    private static final String EXPECTED_GRANTED_SUBJECT = SubjectId.newInstance(SubjectIssuer.GOOGLE,
            SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED).toString();

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
                        policyAlgorithm.getSubjectIdsWithPartialPermission(
                                PoliciesResourceType.thingResource(resource), "WRITE")
                                .contains(EXPECTED_GRANTED_SUBJECT),
                "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
