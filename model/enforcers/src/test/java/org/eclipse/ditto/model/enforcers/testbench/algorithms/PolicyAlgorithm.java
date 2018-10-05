/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.enforcers.testbench.algorithms;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.model.policies.ResourceKey;

/**
 * Interface for policy enforcing algorithms providing default implementation of the {@link Enforcer} interface.
 */
public interface PolicyAlgorithm extends Enforcer {

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
