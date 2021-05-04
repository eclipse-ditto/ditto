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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1;

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
public class Scenario1Simple1 implements Scenario1Simple {

    private final ScenarioSetup setup;

    public Scenario1Simple1() {
        final AuthorizationSubject subjectId =
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED));
        final String resource = "/";
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ+WRITE granted on '" + resource + "'. Is able to READ and WRITE '" + resource + "'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_ALL_GRANTED),
                resource,
                Collections.singleton(subjectId),
                policyAlgorithm -> policyAlgorithm.getSubjectsWithPartialPermission(
                        PoliciesResourceType.thingResource(resource), Permissions.newInstance("READ"))
                        .contains(subjectId),
                "READ", "WRITE");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
