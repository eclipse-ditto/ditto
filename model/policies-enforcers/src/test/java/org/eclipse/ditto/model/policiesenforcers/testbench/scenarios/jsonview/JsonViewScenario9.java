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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.jsonview;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;


@State(Scope.Benchmark)
public class JsonViewScenario9 implements JsonViewScenario {

    private final ScenarioSetup setup;

    public JsonViewScenario9() {
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ granted on '/attributes/attr2'. "
                        + "Subject has READ granted on '/attributes/location'. " //
                        + "Subject has READ granted on '/features/firmware/properties/modulesVersions/b'. " //
                        + "Subject has READ granted on '/features/foo/properties/special'. " //
                        + "Is able to READ '/'. Can see in JsonView: all of those above.", //
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_SOME_GRANTED), //
                "/", //
                THING, //
                THING.toJson(JsonFieldSelector.newInstance("/attributes/attr2", "/attributes/location/latitude",
                        "/attributes/location/longitude",
                        "/features/firmware/properties/modulesVersions/b", "/features/foo/properties/special")), //
                Stream.of(
                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED).toString())
                        .collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
