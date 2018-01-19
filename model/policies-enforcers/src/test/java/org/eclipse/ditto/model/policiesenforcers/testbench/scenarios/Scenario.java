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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.testbench.algorithms.PolicyAlgorithm;


public interface Scenario {

    String THING_TYPE = "thing";

    static ScenarioSetup newScenarioSetup(final boolean expectedResult,
            final String description,
            final Policy policy,
            final AuthorizationContext authorizationContext,
            final String resource,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, description, null);
    }

    static ScenarioSetup newScenarioSetup(final boolean expectedResult,
            final String description,
            final Policy policy,
            final AuthorizationContext authorizationContext,
            final String resource,
            final Set<String> expectedSubjectIds,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, description, expectedSubjectIds);
    }

    static ScenarioSetup newScenarioSetup(final boolean expectedResult,
            final String description,
            final Policy policy,
            final AuthorizationContext authorizationContext,
            final String resource,
            final Set<String> expectedSubjectIds,
            final Function<PolicyAlgorithm, Boolean> additionalAlgorithmFunction,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, description, expectedSubjectIds,
                additionalAlgorithmFunction);
    }

    static ScenarioSetup newScenarioSetup(final boolean expectedResult,
            final String description,
            final Policy policy,
            final AuthorizationContext authorizationContext,
            final String resource,
            final Jsonifiable.WithFieldSelectorAndPredicate<JsonField> fullJsonifiable,
            final JsonObject expectedJsonView,
            final Set<String> expectedSubjectIds,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, fullJsonifiable, expectedJsonView,
                description, expectedSubjectIds);
    }

    static AuthorizationContext newAuthorizationContext(final String authorizationSubject,
            final String... authorizationSubjects) {

        final List<AuthorizationSubject> authorizationSubjectList = Arrays.stream(authorizationSubjects)
                .map(s -> SubjectId.newInstance(SubjectIssuer.GOOGLE, s).toString())
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());

        return AuthorizationContext.newInstance(AuthorizationSubject.newInstance(
                SubjectId.newInstance(SubjectIssuer.GOOGLE, authorizationSubject)),
                authorizationSubjectList.toArray(new AuthorizationSubject[authorizationSubjects.length]));
    }

    default String getName() {
        return getClass().getSimpleName();
    }

    ScenarioSetup getSetup();

    String getScenarioGroup();

    Policy getPolicy();

    Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction();

}
