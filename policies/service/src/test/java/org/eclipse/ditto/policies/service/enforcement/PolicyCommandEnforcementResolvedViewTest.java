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
package org.eclipse.ditto.policies.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Regression tests for the source-side READ check in {@link PolicyCommandEnforcement#filterResponse} when
 * {@code policy-view=resolved} is requested. Locks in the protection against the disclosure leak where a caller
 * with broad READ on the importing policy could read full contents of imported / namespace-root source policies
 * they have no permission on. See PR review on #2429 / #2354.
 *
 * <p>Post-#2431 the ns-root entries appear in the merged policy under the rewritten label
 * {@code nsimported-<rootId>-<originalLabel>}; the assertions reflect that form.</p>
 */
public final class PolicyCommandEnforcementResolvedViewTest {

    private static final SubjectId VIEWER_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "viewer");
    private static final SubjectId ADMIN_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "admin");
    private static final Subject VIEWER = Subject.newInstance(VIEWER_ID);
    private static final Subject ADMIN = Subject.newInstance(ADMIN_ID);

    private static final ResourceKey POLICY_ROOT = PoliciesResourceType.policyResource("/");
    private static final ResourceKey THING_ROOT = PoliciesResourceType.thingResource("/");

    private static final PolicyId IMPORTING_ID = PolicyId.of("test.nsleak", "importing");
    private static final PolicyId DECLARED_SOURCE_ID = PolicyId.of("test.nsleak", "declared-source");
    private static final PolicyId NS_ROOT_ID = PolicyId.of("test.nsleak", "ns-root");
    private static final String IMPORTING_NAMESPACE = "test.nsleak";

    private static final DittoHeaders RESOLVED_VIEW_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(VIEWER_ID)))
            .putHeader(DittoHeaderDefinition.POLICY_VIEW.getKey(), "resolved")
            .correlationId("resolved-view-test")
            .build();

    /**
     * VIEWER has READ+WRITE on policy:/ of the importing policy and zero presence in the declared-import source.
     * Without the source-side check, the merged JSON exposes SECRET (with ADMIN's subject) in cleartext to VIEWER.
     * With the check, SECRET must be absent.
     */
    @Test
    public void resolvedViewDropsImportedEntriesWhenCallerLacksSourceSideRead() throws Exception {
        final Policy importing = Policy.newBuilder(IMPORTING_ID)
                .forLabel("VIEWER")
                .setSubject(VIEWER)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .setPolicyImports(PolicyImports.newInstance(PoliciesModelFactory.newPolicyImport(
                        DECLARED_SOURCE_ID, EffectedImports.newInstance(Collections.emptyList()))))
                .build();
        final Policy declaredSource = Policy.newBuilder(DECLARED_SOURCE_ID)
                .forLabel("ADMIN")
                .setSubject(ADMIN)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .forLabel("SECRET")
                .setSubject(ADMIN)
                .setGrantedPermissions(THING_ROOT, Permission.READ)
                .setImportable(ImportableType.IMPLICIT)
                .build();

        final JsonObject filteredEntries = runResolvedView(importing,
                policyResolver(importing, declaredSource), emptyNamespacePoliciesConfig());

        // Declared-import label format: imported-<sourceId>-<originalLabel>.
        final String secretLabel = "imported-" + DECLARED_SOURCE_ID + "-SECRET";
        assertThat(filteredEntries.contains(JsonPointer.of(secretLabel)))
                .as("imported SECRET entry must be hidden from caller without source-side READ")
                .isFalse();
        assertThat(filteredEntries.toString())
                .as("ADMIN subject must not leak via imported entry")
                .doesNotContain(ADMIN_ID.toString());
    }

    /**
     * VIEWER has READ+WRITE on policy:/ of the importing policy and zero presence in the namespace-root source.
     * Post-#2431 the ns-root entry would land in merged JSON under {@code nsimported-<rootId>-AUDIT}; the
     * source-side check must drop it because VIEWER has no READ on AUDIT in the ns-root.
     */
    @Test
    public void resolvedViewDropsNamespaceRootEntriesWhenCallerLacksSourceSideRead() throws Exception {
        final Policy importing = Policy.newBuilder(IMPORTING_ID)
                .forLabel("VIEWER")
                .setSubject(VIEWER)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .build();
        final Policy nsRoot = Policy.newBuilder(NS_ROOT_ID)
                .forLabel("ADMIN")
                .setSubject(ADMIN)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .forLabel("AUDIT")
                .setSubject(ADMIN)
                .setGrantedPermissions(THING_ROOT, Permission.READ)
                .setImportable(ImportableType.IMPLICIT)
                .build();

        final JsonObject filteredEntries = runResolvedView(importing,
                policyResolver(importing, nsRoot),
                namespacePoliciesConfigBinding(IMPORTING_NAMESPACE, NS_ROOT_ID));
        // Post-#2431 the ns-root entry's merged label is the rewritten nsimported-<rootId>-<originalLabel> form.
        final String auditLabel = PoliciesModelFactory.newNsImportedLabel(NS_ROOT_ID, "AUDIT").toString();
        assertThat(filteredEntries.contains(JsonPointer.of(auditLabel)))
                .as("namespace-root AUDIT entry must be hidden from caller without source-side READ")
                .isFalse();
        assertThat(filteredEntries.toString())
                .as("ADMIN subject must not leak via namespace-root entry")
                .doesNotContain(ADMIN_ID.toString());
    }

    /**
     * Drives {@link PolicyCommandEnforcement#filterResponse} end-to-end for a resolved-view request and returns the
     * filtered {@code entries} JSON object so each test can assert on it. The importing-policy enforcer passed to
     * {@code filterResponse} is built via {@link PolicyEnforcer#withResolvedImportsAndNamespacePolicies} with the
     * same resolver + namespace config the production cache loader uses, mirroring runtime behaviour exactly.
     */
    private static JsonObject runResolvedView(final Policy importing,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> resolver,
            final NamespacePoliciesConfig namespacePoliciesConfig) throws Exception {
        final PolicyCommandEnforcement enforcement =
                new PolicyCommandEnforcement(resolver, namespacePoliciesConfig);
        final PolicyEnforcer importingEnforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(importing, resolver, namespacePoliciesConfig)
                .toCompletableFuture().get();
        final RetrievePolicyResponse rawResponse = RetrievePolicyResponse.of(IMPORTING_ID,
                importing.toJson(), RESOLVED_VIEW_HEADERS);

        final PolicyCommandResponse<?> filtered = enforcement.filterResponse(rawResponse, importingEnforcer)
                .toCompletableFuture().get();

        final RetrievePolicyResponse retrieveResp = (RetrievePolicyResponse) filtered;
        return retrieveResp.getEntity().asObject().getValue("entries").orElseThrow().asObject();
    }

    private static Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver(final Policy... policies) {
        return id -> {
            for (final Policy policy : policies) {
                if (policy.getEntityId().filter(pid -> pid.equals(id)).isPresent()) {
                    return CompletableFuture.completedFuture(Optional.of(policy));
                }
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };
    }

    private static NamespacePoliciesConfig emptyNamespacePoliciesConfig() {
        final NamespacePoliciesConfig config = Mockito.mock(NamespacePoliciesConfig.class);
        when(config.isEmpty()).thenReturn(true);
        when(config.getNamespacePolicies()).thenReturn(Collections.emptyMap());
        when(config.getRootPoliciesForNamespace(Mockito.anyString())).thenReturn(Collections.emptyList());
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Collections.emptySet());
        when(config.getNamespacesForRootPolicy(Mockito.any())).thenReturn(Collections.emptySet());
        return config;
    }

    private static NamespacePoliciesConfig namespacePoliciesConfigBinding(final String namespace,
            final PolicyId nsRootPolicyId) {
        final NamespacePoliciesConfig config = Mockito.mock(NamespacePoliciesConfig.class);
        when(config.isEmpty()).thenReturn(false);
        when(config.getRootPoliciesForNamespace(namespace)).thenReturn(List.of(nsRootPolicyId));
        when(config.getRootPoliciesForNamespace(Mockito.argThat(ns -> !namespace.equals(ns))))
                .thenReturn(Collections.emptyList());
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Collections.singleton(nsRootPolicyId));
        return config;
    }
}
