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
public class JsonViewScenario6 implements JsonViewScenario {

    private final ScenarioSetup setup;

    public JsonViewScenario6() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ granted on '/features/foo'. "
                        + "Subject has READ granted on '/attributes'. " //
                        + "Subject has READ revoked on '/features/foo/properties/special'. " //
                        + "Is able to READ '/'. Can see in JsonView: '/features/foo/properties/ordinary'.", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED,
                        SUBJECT_ATTRIBUTES_ALL_GRANTED), //
                "/", //
                THING, //
                THING.get(JsonFieldSelector.newInstance("/attributes", "/features/foo/properties/ordinary")), //
                Stream.of(AuthorizationSubject.newInstance(
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED))).collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
