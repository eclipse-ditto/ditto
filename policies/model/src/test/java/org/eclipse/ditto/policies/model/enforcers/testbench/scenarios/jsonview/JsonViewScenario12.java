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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Tests JSON conversion with non-root resource key.
 */
@State(Scope.Benchmark)
public class JsonViewScenario12 implements JsonViewScenario {

    private final ScenarioSetup setup;

    public JsonViewScenario12() {
        final String resource = "/attributes";
        setup = Scenario.newScenarioSetup(
                true,
                "Subject has READ granted on '/'. "
                        + "Subject has READ granted on '/attributes/location/nonexistentAttribute'. "
                        + "Subject has READ revoked on '" + resource + "'. "
                        + "Is not able to READ '" + resource + "'. "
                        + "Cannot see in JsonView: '" + resource + "'",
                getPolicy(),
                Scenario.newAuthorizationContext(SUBJECT_NONEXISTENT_ATTRIBUTE_GRANTED),
                resource,
                THING,
                JsonFactory.newObject(),
                null,
                "READ");
    }

    @Override
    public ScenarioSetup getSetup() {
        return setup;
    }

}
