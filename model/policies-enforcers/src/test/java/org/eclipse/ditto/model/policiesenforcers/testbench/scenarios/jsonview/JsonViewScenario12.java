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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;
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
