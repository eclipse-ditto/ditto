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
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.PolicyModifyCommandsProvider.Outcome.ERROR;
import static org.eclipse.ditto.policies.enforcement.pre.PolicyImportsPreEnforcerTest.PolicyModifyCommandsProvider.Outcome.SUCCESS;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
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
                        .withCauseInstanceOf(PolicyNotAccessibleException.class)
                        .withMessageContaining(IMPORTED_POLICY_ID.toString());
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

    static class PolicyModifyCommandsProvider implements ArgumentsProvider {

        public static final Set<Set<String>> ALL_SUBJECTS =
                Set.of(Set.of("implicit"), Set.of("explicit"), Set.of("never"), Set.of("implicit", "explicit"),
                        Set.of("implicit", "never"), Set.of("explicit", "never"),
                        Set.of("implicit", "explicit", "never"));

        private PolicyModifyCommandsProvider() {
        }

        private static DittoHeaders getDittoHeaders(final Set<String> subjects) {

            final AuthorizationContext authContext =
                    AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                            subjects.stream()
                                    .map(s -> "ditto:" + s)
                                    .map(AuthorizationSubject::newInstance)
                                    .collect(Collectors.toList()));

            return DittoHeaders.newBuilder().authorizationContext(authContext).build();
        }

        private static Policy getPolicy(final Set<String> importedLabels) {
            final EffectedImports effectedImports = EffectedImports.newInstance(
                    importedLabels.stream().map(Label::of).collect(Collectors.toList()));

            final PolicyImport policyImport = PolicyImport.newInstance(IMPORTED_POLICY_ID, effectedImports);
            final PolicyImports policyImports = PolicyImports.newInstance(policyImport);

            return IMPORTING.toBuilder().setPolicyImports(policyImports).build();
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            return Stream.of(
                    // no labels defined (import all implicit entries)
                    of(
                            // imported labels
                            Set.of(),
                            // combinations of subjects allowed to modify
                            Set.of(
                                    Set.of("implicit"),
                                    Set.of("explicit", "implicit"),
                                    Set.of("never", "implicit"),
                                    Set.of("explicit", "implicit", "never")
                            )
                    ),

                    // only explicit label defined (import implicit + explicit entries)
                    of(Set.of("EXPLICIT"),
                            Set.of(Set.of("explicit", "implicit"), Set.of("explicit", "implicit", "never"))),

                    // only never labels defined (import implicit + never entries)
                    of(Set.of("NEVER"),
                            Set.of(Set.of("implicit", "never"), Set.of("explicit", "implicit", "never"))),

                    // only implicit + explicit labels defined (import implicit + explicit entries)
                    of(Set.of("IMPLICIT", "EXPLICIT"),
                            Set.of(Set.of("implicit", "explicit"), Set.of("explicit", "implicit", "never"))),

                    // only implicit + never labels defined (import implicit + never entries)
                    of(Set.of("IMPLICIT", "NEVER"),
                            Set.of(Set.of("implicit", "never"), Set.of("explicit", "implicit", "never"))),

                    // only explicit + never labels defined (import explicit + never entries)
                    of(Set.of("EXPLICIT", "NEVER"),
                            Set.of(Set.of("explicit", "implicit", "never"))),

                    // implicit + explicit + never labels defined (import implicit + explicit + never entries)
                    of(Set.of("IMPLICIT", "EXPLICIT", "NEVER"),
                            Set.of(Set.of("explicit", "implicit", "never")))
            ).reduce(Stream::concat).orElseThrow();
        }

        private static Stream<Arguments> of(final Set<String> imports, final Set<Set<String>> subjectsWithAccess) {
            final Set<Set<String>> subjectsWithoutSuccess = new HashSet<>(ALL_SUBJECTS);
            subjectsWithoutSuccess.removeAll(subjectsWithAccess);
            return Stream.concat(
                    // expect SUCCESS for subjects with access
                    subjectsWithAccess.stream()
                            .map(PolicyModifyCommandsProvider::getDittoHeaders)
                            .flatMap(headers -> buildCommands(SUCCESS, getPolicy(imports), headers)),
                    // expect ERROR for all other combinations of subjects
                    subjectsWithoutSuccess.stream()
                            .map(PolicyModifyCommandsProvider::getDittoHeaders)
                            .flatMap(headers -> buildCommands(ERROR, getPolicy(imports), headers))
            );
        }

        private static Stream<Arguments> buildCommands(final Outcome outcome, final Policy policy,
                final DittoHeaders dittoHeaders) {
            final PolicyId policyId = policy.getEntityId().orElseThrow();
            return Stream.of(
                    Arguments.of(outcome, CreatePolicy.of(policy, dittoHeaders)),
                    Arguments.of(outcome, ModifyPolicy.of(policyId, policy, dittoHeaders)),
                    Arguments.of(outcome, ModifyPolicyImports.of(policyId, policy.getPolicyImports(), dittoHeaders)),
                    Arguments.of(outcome, ModifyPolicyImport.of(policyId,
                            policy.getPolicyImports().getPolicyImport(IMPORTED_POLICY_ID).orElseThrow(), dittoHeaders))
            );
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
                                "ditto:admin" : { "type": "test" }
                            },
                            "resources": {
                                "policy:/": { "grant": [ "READ", "WRITE" ], "revoke": [] }
                            }
                        }
                    },
                    "imports": {
                        "test:imported": { "entries": [ "EXPLICIT" ] }
                    }
                }
                """);
        static final Policy IMPORTED = PoliciesModelFactory.newPolicy("""
                {
                    "policyId": "test:imported",
                    "entries" : {
                        "DEFAULT" : {
                            "subjects": {
                                "ditto:admin" : { "type": "test" }
                            },
                            "resources": {
                                "policy:/": { "grant": [ "READ", "WRITE" ], "revoke": [] }
                            },
                            "importable":"never"
                        },
                        "EXPLICIT" : {
                            "subjects": {
                                "ditto:explicit": { "type": "test" }
                            },
                            "resources": {
                                "policy:/entries/EXPLICIT": { "grant": [ "READ" ], "revoke": [] }
                            },
                            "importable": "explicit"
                        },
                        "IMPLICIT" : {
                            "subjects": {
                                "ditto:implicit" : { "type": "test" }
                            },
                            "resources": {
                                "policy:/entries/IMPLICIT": { "grant": [ "READ" ], "revoke": [] }
                            },
                            "importable": "implicit"
                        },
                        "NEVER" : {
                            "subjects": {
                                "ditto:never" : { "type": "test" }
                            },
                            "resources": {
                                "policy:/entries/NEVER": { "grant": [ "READ" ], "revoke": [] }
                            },
                            "importable": "never"
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
                        "test:notfound": { "entries": [ "IMPORT" ] }
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