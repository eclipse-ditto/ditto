/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.pre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.IMPORTED;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.IMPORTED_POLICY_ID;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.IMPORTING;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.IMPORTING_POLICY_ID;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.IMPORT_NOT_FOUND;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.IMPORT_NOT_FOUND_POLICY_ID;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.Policies.KNOWN_IDS;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

/**
 * Tests {@link org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcer}.
 */
class PolicyImportsPreEnforcerTest {

    private static final AuthorizationContext AUTH_CONTEXT_SUBJECT_ALLOWED = AuthorizationModelFactory.newAuthContext(
            JsonObject.of("""
                        {
                            "type" : "unspecified",
                            "subjects" : ["ditto:subject1"]
                        }
                    """));
    private static final AuthorizationContext AUTH_CONTEXT_SUBJECT_FORBIDDEN = AuthorizationModelFactory.newAuthContext(
            JsonObject.of("""
                        {
                            "type" : "unspecified",
                            "subjects" : ["ditto:subject2"]
                        }
                    """));
    private PolicyImportsPreEnforcer policyImportsPreEnforcer;

    @BeforeEach
    void setUp() {
        PolicyEnforcerProvider policyEnforcerProvider = Mockito.mock(PolicyEnforcerProvider.class);

        when(policyEnforcerProvider.getPolicyEnforcer(IMPORTED_POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(IMPORTED))));
        when(policyEnforcerProvider.getPolicyEnforcer(IMPORTING_POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(IMPORTING))));
        when(policyEnforcerProvider.getPolicyEnforcer(IMPORT_NOT_FOUND_POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(IMPORT_NOT_FOUND))));
        when(policyEnforcerProvider.getPolicyEnforcer(argThat(id -> !KNOWN_IDS.contains(id))))
                .thenReturn(CompletableFuture.completedStage(Optional.empty()));

        policyImportsPreEnforcer = new PolicyImportsPreEnforcer(policyEnforcerProvider);
    }

    @ParameterizedTest
    @ArgumentsSource(PolicyModifyCommandsProvider.class)
    void testSubjectAllowedToReadImportedPolicy(final PolicyModifyCommandsProvider.Outcome outcome,
            final PolicyModifyCommand<?> command) {
        final CompletableFuture<Signal<?>> applyFuture = policyImportsPreEnforcer.apply(command).toCompletableFuture();
        switch (outcome) {
            case SUCCESS -> {
                final Signal<?> signal = applyFuture.join();
                assertThat(signal).isSameAs(command);
            }
            case ERROR -> {
                assertThatExceptionOfType(CompletionException.class)
                        .isThrownBy(applyFuture::join)
                        .withCauseInstanceOf(PolicyImportNotAccessibleException.class);
            }
        }
    }

    @Test
    void testEnforcerOfImportedPolicyNotFound() {
        final DittoHeaders dittoHeaders =
                DittoHeaders.newBuilder().authorizationContext(AUTH_CONTEXT_SUBJECT_FORBIDDEN).build();

        final ModifyPolicy modifyPolicy = ModifyPolicy.of(IMPORT_NOT_FOUND_POLICY_ID, IMPORT_NOT_FOUND, dittoHeaders);

        final CompletableFuture<Signal<?>> signalFuture =
                policyImportsPreEnforcer.apply(modifyPolicy).toCompletableFuture();

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(signalFuture::join)
                .withCauseInstanceOf(PolicyNotAccessibleException.class);
    }

    private static class PolicyModifyCommandsProvider implements ArgumentsProvider {

        private PolicyModifyCommandsProvider() {
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            return Arrays.stream(Outcome.values())
                    .flatMap(outcome -> Stream.of(
                            Arguments.of(outcome, CreatePolicy.of(IMPORTING, getDittoHeaders(outcome))),
                            Arguments.of(outcome,
                                    ModifyPolicy.of(IMPORTING_POLICY_ID, IMPORTING, getDittoHeaders(outcome))),
                            Arguments.of(outcome,
                                    ModifyPolicyImports.of(IMPORTING_POLICY_ID, IMPORTING.getPolicyImports(),
                                            getDittoHeaders(outcome))),
                            Arguments.of(outcome, ModifyPolicyImport.of(IMPORTING_POLICY_ID,
                                    IMPORTING.getPolicyImports().getPolicyImport(IMPORTED_POLICY_ID).orElseThrow(),
                                    getDittoHeaders(outcome)))
                    ));
        }

        private static DittoHeaders getDittoHeaders(final Outcome outcome) {
            return switch (outcome) {
                case SUCCESS -> DittoHeaders.newBuilder().authorizationContext(AUTH_CONTEXT_SUBJECT_ALLOWED).build();
                case ERROR -> DittoHeaders.newBuilder().authorizationContext(AUTH_CONTEXT_SUBJECT_FORBIDDEN).build();
            };
        }

        enum Outcome {
            SUCCESS,
            ERROR
        }
    }

    static class Policies {
        static final Policy IMPORTING = PoliciesModelFactory.newPolicy("""
            {
                "policyId": "test:importing",
                "entries" : {
                    "DEFAULT" : {
                        "subjects": {
                            "ditto:subject1" : { "type": "test" }
                        },
                        "resources": {
                            "thing:/attributes": { "grant": [ "READ", "WRITE" ], "revoke": [] }
                        }
                    }
                },
                "imports": {
                    "test:imported": {"entries":["IMPORT"]}
                }
            }
            """);
        static final Policy IMPORTED = PoliciesModelFactory.newPolicy("""
            {
                "policyId": "test:imported",
                "entries" : {
                    "DEFAULT" : {
                        "subjects": {
                            "ditto:subject2" : { "type": "test" }
                        },
                        "resources": {
                            "thing:/": { "grant": [ "READ", "WRITE" ], "revoke": [] }
                        },
                        "importable":"never"
                    },
                    "IMPORT" : {
                        "subjects": {
                            "ditto:subject1" : { "type": "test" }
                        },
                        "resources": {
                            "policy:/entries/IMPORT": { "grant": [ "READ" ], "revoke": [] }
                        },
                        "importable":"explicit"
                    }
                }
            }
            """);
        static final Policy IMPORT_NOT_FOUND = PoliciesModelFactory.newPolicy("""
            {
                "policyId": "test:import.not.found",
                "entries" : {
                    "DEFAULT" : {
                        "subjects": {
                            "ditto:subject1" : { "type": "test" }
                        },
                        "resources": {
                            "policy:/": { "grant": [ "READ", "WRITE" ], "revoke": [] }
                        }
                    }
                },
                "imports": {
                    "test:notfound": {"entries":["IMPORT"]}
                }
            }
            """);
        static final PolicyId IMPORTING_POLICY_ID = IMPORTING.getEntityId().orElseThrow();
        static final PolicyId IMPORTED_POLICY_ID = IMPORTED.getEntityId().orElseThrow();
        static final PolicyId IMPORT_NOT_FOUND_POLICY_ID = IMPORT_NOT_FOUND.getEntityId().orElseThrow();

        static final Collection<PolicyId> KNOWN_IDS =
                List.of(IMPORTED_POLICY_ID, IMPORTING_POLICY_ID, IMPORT_NOT_FOUND_POLICY_ID);
    }
}