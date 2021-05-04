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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Tests JSON conversion with non-root resource key.
 */
@State(Scope.Benchmark)
public class JsonViewScenario11 implements JsonViewScenario {

    private final ScenarioSetup setup;

    public JsonViewScenario11() {
        final String resourcePath = "/features/firmware";
        setup = Scenario.newScenarioSetup( //
                true, //
                "Subject has READ granted on '/attributes/attr2'. "
                        + "Subject has READ granted on '/attributes/location'. " //
                        + "Subject has READ granted on '/features/firmware/properties/modulesVersions/b'. " //
                        + "Subject has READ granted on '/features/foo/properties/special'. " //
                        + "Subject has READ revoked on '/attributes/location/latitude'. " //
                        + "Subject has READ revoked on '/features/firmware/properties/modulesVersions'. " //
                        + "Subject has READ revoked on '/features/foo/properties/special'. " //
                        + "Is able to READ '/features/firmware'. "
                        + "Can see in JsonView: granted subresource of /features/firmware",
                getPolicy(), //
                Scenario.newAuthorizationContext(SUBJECT_SOME_GRANTED, SUBJECT_SOME_REVOKED), //
                resourcePath, //
                THING, //
                THING.get(JsonFieldSelector.newInstance("/features/firmware/properties/modulesVersions/b"))
                        .getValue(resourcePath)
                        .map(JsonValue::asObject)
                        .orElseThrow(NullPointerException::new),
                Stream.of(
                        AuthorizationSubject.newInstance(
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED)),
                        AuthorizationSubject.newInstance(
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED)),
                        AuthorizationSubject.newInstance(
                                SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED)))
                        .collect(Collectors.toSet()),
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }
}
