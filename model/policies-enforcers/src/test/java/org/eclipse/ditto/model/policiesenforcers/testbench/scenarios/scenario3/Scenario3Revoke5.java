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
public class Scenario3Revoke5 implements Scenario3Revoke {

    private static final String EXPECTED_GRANTED_SUBJECT = SubjectId.newInstance(SubjectIssuer.GOOGLE,
            SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED).toString();

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
                        policyAlgorithm.getSubjectIdsWithPartialPermission(
                                PoliciesResourceType.thingResource("/attributes"), "READ")
                                .contains(EXPECTED_GRANTED_SUBJECT),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
