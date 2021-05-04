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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class JsonViewScenario3 implements JsonViewScenario {

    private final ScenarioSetup setup;

    public JsonViewScenario3() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ+WRITE granted on '/'. "
                        + "Subject has READ+WRITE revoked on '/attributes'. " //
                        + "Subject has READ granted on '/attributes/location'. " //
                        + "Is able to READ '/'. Can see in JsonView: '/attributes/location' and '/features'.", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED), //
                "/", //
                THING, //
                THING.get(JsonFieldSelector.newInstance("/attributes/location", "/features")), //
                Stream.of(AuthorizationSubject.newInstance(
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED))).collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
