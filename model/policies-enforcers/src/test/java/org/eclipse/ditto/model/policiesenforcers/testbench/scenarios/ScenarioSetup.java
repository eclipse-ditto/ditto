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
package org.eclipse.ditto.model.policiesenforcers.testbench.scenarios;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;


public final class ScenarioSetup {

    private final Policy policy;
    private final AuthorizationContext authorizationContext;
    private final JsonPointer resource;
    private final String type;
    private final Permissions requiredPermissions;
    private final boolean expectedResult;
    private final Jsonifiable.WithFieldSelectorAndPredicate<JsonField> fullJsonifiable;
    private final JsonObject expectedJsonView;
    private final Set<String> expectedSubjectIds;
    private final String description;
    private final Function<PolicyAlgorithm, Boolean> additionalAlgorithmFunction;

    public ScenarioSetup(final Policy policy,
            final AuthorizationContext authorizationContext,
            final JsonPointer resource,
            final String type,
            final Permissions requiredPermissions,
            final boolean expectedResult,
            final String description,
            final Set<String> expectedSubjectIds) {

        this(policy, authorizationContext, resource, type, requiredPermissions, expectedResult, null, null, description,
                expectedSubjectIds, null);
    }

    public ScenarioSetup(final Policy policy,
            final AuthorizationContext authorizationContext,
            final JsonPointer resource,
            final String type,
            final Permissions requiredPermissions,
            final boolean expectedResult,
            final String description,
            final Set<String> excpectedSubjectIds,
            final Function<PolicyAlgorithm, Boolean> additionalAlgorithmFunction) {

        this(policy, authorizationContext, resource, type, requiredPermissions, expectedResult, null, null, description,
                excpectedSubjectIds, additionalAlgorithmFunction);
    }

    public ScenarioSetup(final Policy policy,
            final AuthorizationContext authorizationContext,
            final JsonPointer resource,
            final String type,
            final Permissions requiredPermissions,
            final boolean expectedResult,
            final Jsonifiable.WithFieldSelectorAndPredicate<JsonField> fullJsonifiable,
            final JsonObject expectedJsonView,
            final String description,
            final Set<String> excpectedSubjectIds) {

        this(policy, authorizationContext, resource, type, requiredPermissions, expectedResult, fullJsonifiable,
                expectedJsonView, description, excpectedSubjectIds, null);
    }

    public ScenarioSetup(final Policy policy,
            final AuthorizationContext authorizationContext,
            final JsonPointer resource,
            final String type,
            final Permissions requiredPermissions,
            final boolean expectedResult,
            final Jsonifiable.WithFieldSelectorAndPredicate<JsonField> fullJsonifiable,
            final JsonObject expectedJsonView,
            final String description,
            final Set<String> expectedSubjectIds,
            final Function<PolicyAlgorithm, Boolean> additionalAlgorithmFunction) {

        this.policy = policy;
        this.authorizationContext = authorizationContext;
        this.resource = resource;
        this.requiredPermissions = requiredPermissions;
        this.expectedResult = expectedResult;
        this.fullJsonifiable = fullJsonifiable;
        this.description = description;
        this.expectedJsonView = expectedJsonView;
        this.expectedSubjectIds = expectedSubjectIds;
        this.type = type;
        this.additionalAlgorithmFunction = additionalAlgorithmFunction;
    }

    public Policy getPolicy() {
        return policy;
    }

    public AuthorizationContext getAuthorizationContext() {
        return authorizationContext;
    }

    public JsonPointer getResource() {
        return resource;
    }

    public Permissions getRequiredPermissions() {
        return requiredPermissions;
    }

    public boolean getExpectedResult() {
        return expectedResult;
    }

    public Optional<Jsonifiable.WithFieldSelectorAndPredicate<JsonField>> getFullJsonifiable() {
        return Optional.ofNullable(fullJsonifiable);
    }

    public Optional<Set<String>> getExpectedSubjectIds() {
        return Optional.ofNullable(expectedSubjectIds);
    }

    public Optional<JsonObject> getExpectedJsonView() {
        return Optional.ofNullable(expectedJsonView);
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public Optional<Function<PolicyAlgorithm, Boolean>> getAdditionalAlgorithmFunction() {
        return Optional.ofNullable(additionalAlgorithmFunction);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "policy=\n" + policy.toJsonString() +
                ",\nauthorizationContext=" + authorizationContext +
                ",\nresource=" + resource +
                ",\nrequiredPermissions=" + requiredPermissions +
                ",\nexpectedResult=" + expectedResult +
                ",\nfullJsonifiable=" + fullJsonifiable +
                ",\nexpectedJsonView=" + expectedJsonView +
                ",\ndescription=" + description + "]";
    }

}
