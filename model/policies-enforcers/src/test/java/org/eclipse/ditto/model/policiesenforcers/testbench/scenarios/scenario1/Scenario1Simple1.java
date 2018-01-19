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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.scenario1;

import java.util.Collections;

import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class Scenario1Simple1 implements Scenario1Simple {

    private final ScenarioSetup setup;

    public Scenario1Simple1() {
        final String subjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED).toString();
        final String resource = "/";
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ+WRITE granted on '" + resource + "'. Is able to READ and WRITE '" + resource + "'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ALL_GRANTED),
                resource,
                Collections.singleton(subjectId),
                policyAlgorithm -> policyAlgorithm.getSubjectIdsWithPartialPermission(
                        PoliciesResourceType.thingResource(resource), "READ").contains(subjectId),
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
