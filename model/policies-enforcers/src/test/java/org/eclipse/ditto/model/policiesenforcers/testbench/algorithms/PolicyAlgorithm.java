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
package org.eclipse.ditto.model.policiesenforcers.testbench.algorithms;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.testbench.scenarios.ScenarioSetup;

/**
 * Interface for policy enforcing algorithms providing default implementation of the {@link PolicyEnforcer} interface.
 */
public interface PolicyAlgorithm extends PolicyEnforcer {

    default String getName() {
        return getClass().getSimpleName();
    }

    default boolean hasPermissionsOnResource(final ScenarioSetup scenarioSetup) {
        return hasUnrestrictedPermissions(ResourceKey.newInstance(scenarioSetup.getType(), scenarioSetup.getResource()),
                scenarioSetup.getAuthorizationContext(), scenarioSetup.getRequiredPermissions());
    }

    default boolean hasPermissionsOnResourceOrAnySubresource(final ScenarioSetup scenarioSetup) {
        return hasPartialPermissions(ResourceKey.newInstance(scenarioSetup.getType(), scenarioSetup.getResource()),
                scenarioSetup.getAuthorizationContext(), scenarioSetup.getRequiredPermissions());
    }

    default JsonObject buildJsonView(final Iterable<JsonField> jsonFields, final ScenarioSetup scenarioSetup) {
        return buildJsonView(ResourceKey.newInstance(scenarioSetup.getType(), scenarioSetup.getResource()), jsonFields,
                scenarioSetup.getAuthorizationContext(), scenarioSetup.getRequiredPermissions());
    }

}
