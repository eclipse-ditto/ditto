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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


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
            final Set<AuthorizationSubject> expectedSubjects,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, description, expectedSubjects);
    }

    static ScenarioSetup newScenarioSetup(final boolean expectedResult,
            final String description,
            final Policy policy,
            final AuthorizationContext authorizationContext,
            final String resource,
            final Set<AuthorizationSubject> expectedSubjects,
            final Function<PolicyAlgorithm, Boolean> additionalAlgorithmFunction,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, description, expectedSubjects,
                additionalAlgorithmFunction);
    }

    static ScenarioSetup newScenarioSetup(final boolean expectedResult,
            final String description,
            final Policy policy,
            final AuthorizationContext authorizationContext,
            final String resource,
            final JsonObject fullJsonObject,
            final JsonObject expectedJsonView,
            final Set<AuthorizationSubject> expectedSubjects,
            final String permission,
            final String... permissions) {

        return new ScenarioSetup(policy, authorizationContext, JsonPointer.of(resource), THING_TYPE,
                Permissions.newInstance(permission, permissions), expectedResult, fullJsonObject, expectedJsonView,
                description, expectedSubjects);
    }

    static AuthorizationContext newAuthorizationContext(final String authorizationSubject,
            final String... authorizationSubjects) {

        final List<AuthorizationSubject> authorizationSubjectList = Arrays.stream(authorizationSubjects)
                .map(s -> SubjectId.newInstance(SubjectIssuer.GOOGLE, s).toString())
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());

        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationSubject.newInstance(SubjectId.newInstance(SubjectIssuer.GOOGLE, authorizationSubject)),
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
