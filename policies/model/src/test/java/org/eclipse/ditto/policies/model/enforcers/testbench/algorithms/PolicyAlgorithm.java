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
package org.eclipse.ditto.policies.model.enforcers.testbench.algorithms;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.ResourceKey;

/**
 * Interface for policy enforcing algorithms providing default implementation of the {@link org.eclipse.ditto.policies.model.enforcers.Enforcer} interface.
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
