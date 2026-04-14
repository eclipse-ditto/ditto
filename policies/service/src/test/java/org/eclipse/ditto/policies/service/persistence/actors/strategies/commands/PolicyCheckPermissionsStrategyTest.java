/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissions;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissionsResponse;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link PolicyCheckPermissionsStrategy}.
 */
public final class PolicyCheckPermissionsStrategyTest extends AbstractPolicyCommandStrategyTest {

    private static final PolicyId IMPORTING_POLICY_ID = PolicyId.of("org.eclipse.ditto", "importing-policy");
    private static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("org.eclipse.ditto", "imported-policy");
    private static final Label IMPORTED_LABEL = Label.of("operator");
    private static final SubjectId AUTH_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "test-user");
    private static final Subject AUTH_SUBJECT = Subject.newInstance(AUTH_SUBJECT_ID, SubjectType.GENERATED);
    private static final ResourceKey IMPORTED_MESSAGE_RESOURCE =
            ResourceKey.newInstance("message", "/features/lamp/inbox/messages/toggle");
    private static final ResourceKey THING_ROOT_RESOURCE = ResourceKey.newInstance("thing", "/");
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(
                    DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(AUTH_SUBJECT_ID.toString())))
            .build();

    private Policy importedPolicy;

    @Before
    public void setUp() {
        importedPolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTED_POLICY_ID)
                .forLabel(IMPORTED_LABEL)
                .setSubject(AUTH_SUBJECT)
                .setGrantedPermissions(IMPORTED_MESSAGE_RESOURCE, Permissions.newInstance("WRITE"))
                .setImportable(ImportableType.EXPLICIT)
                .build();
    }

    @Test
    public void checkPermissionsShouldHonorImportedEntries() {
        final Policy importingPolicy = PoliciesModelFactory.newPolicyBuilder(IMPORTING_POLICY_ID)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(
                        IMPORTED_POLICY_ID,
                        EffectedImports.newInstance(List.of(IMPORTED_LABEL))
                ))
                .build();

        // Sanity check: the resolved enforcer does grant the permission
        final var resolvedEnforcer = PolicyEnforcer.withResolvedImports(
                importingPolicy,
                policyId -> CompletableFuture.completedFuture(
                        IMPORTED_POLICY_ID.equals(policyId) ? Optional.of(importedPolicy) : Optional.empty()
                )
        ).toCompletableFuture().join();
        assertThat(resolvedEnforcer.getEnforcer().hasUnrestrictedPermissions(
                IMPORTED_MESSAGE_RESOURCE,
                DITTO_HEADERS.getAuthorizationContext(),
                Permissions.newInstance("WRITE")))
                .describedAs("sanity check: the imported policy grants WRITE on the message resource")
                .isTrue();

        // Provider returns the resolved enforcer
        final PolicyEnforcerProvider provider = policyId ->
                PolicyEnforcer.withResolvedImports(importingPolicy,
                        importedPolicyId -> CompletableFuture.completedFuture(
                                IMPORTED_POLICY_ID.equals(importedPolicyId)
                                        ? Optional.of(importedPolicy)
                                        : Optional.empty()
                        )).thenApply(Optional::of);

        final PolicyCheckPermissionsStrategy underTest = new PolicyCheckPermissionsStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")), provider);

        final ResourcePermissions permissions = ResourcePermissionFactory.newInstance(
                IMPORTED_MESSAGE_RESOURCE,
                List.of("WRITE")
        );
        final CheckPolicyPermissions command = CheckPolicyPermissions.of(
                IMPORTING_POLICY_ID,
                Map.of("toggle", permissions),
                DITTO_HEADERS
        );

        final Result<?> result = applyStrategy(underTest, getDefaultContext(), importingPolicy, command);

        final Dummy<?> mock = Dummy.mock();
        result.accept((ResultVisitor) mock, null);

        final ArgumentCaptor<CompletionStage<WithDittoHeaders>> responseCaptor =
                ArgumentCaptor.forClass(CompletionStage.class);
        verify(mock).onStagedQuery(any(), responseCaptor.capture(), isNull());

        final CheckPolicyPermissionsResponse response =
                (CheckPolicyPermissionsResponse) responseCaptor.getValue().toCompletableFuture().join();
        assertThat(CheckPolicyPermissionsResponse.toMap(response.getPermissionsResults()))
                .containsEntry("toggle", true);
    }

    @Test
    public void checkPermissionsWithLocalEntriesOnly() {
        final PolicyId localPolicyId = PolicyId.of("org.eclipse.ditto", "local-policy");
        final Policy localPolicy = PoliciesModelFactory.newPolicyBuilder(localPolicyId)
                .forLabel(Label.of("operator"))
                .setSubject(AUTH_SUBJECT)
                .setGrantedPermissions(THING_ROOT_RESOURCE, Permissions.newInstance("READ", "WRITE"))
                .build();

        // Provider returns the enforcer built from local policy (no imports to resolve)
        final PolicyEnforcerProvider provider = policyId ->
                CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(localPolicy)));

        final PolicyCheckPermissionsStrategy underTest = new PolicyCheckPermissionsStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")), provider);

        final ResourcePermissions permissions = ResourcePermissionFactory.newInstance(
                THING_ROOT_RESOURCE,
                List.of("READ")
        );
        final CheckPolicyPermissions command = CheckPolicyPermissions.of(
                localPolicyId,
                Map.of("thingRead", permissions),
                DITTO_HEADERS
        );

        final Result<?> result = applyStrategy(underTest, getDefaultContext(), localPolicy, command);

        final Dummy<?> mock = Dummy.mock();
        result.accept((ResultVisitor) mock, null);

        final ArgumentCaptor<CompletionStage<WithDittoHeaders>> responseCaptor =
                ArgumentCaptor.forClass(CompletionStage.class);
        verify(mock).onStagedQuery(any(), responseCaptor.capture(), isNull());

        final CheckPolicyPermissionsResponse response =
                (CheckPolicyPermissionsResponse) responseCaptor.getValue().toCompletableFuture().join();
        assertThat(CheckPolicyPermissionsResponse.toMap(response.getPermissionsResults()))
                .containsEntry("thingRead", true);
    }
}
