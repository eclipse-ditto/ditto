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
package org.eclipse.ditto.policies.model.enforcers.trie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.SubjectType;
import org.junit.Test;

public class TrieBasedPolicyEnforcerTest {

    @Test
    public void buildJsonView() {
        final TrieBasedPolicyEnforcer underTest =
                TrieBasedPolicyEnforcer.newInstance(defaultPolicy(PolicyId.of("namespace", "id")));

        final JsonObject createdJsonView = underTest.buildJsonView(
                ResourceKey.newInstance("foo", "bar"),
                JsonFactory.nullObject(),
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("itsMe")),
                Permissions.none());

        final JsonObject expectedJsonView = JsonFactory.nullObject();

        assertThat(createdJsonView).isEqualTo(expectedJsonView);
    }

    @Test
    public void getSubjectsWithUnrestrictedPermissionDoesNotIncludeRevoked() {
        final String featureId = "Lamp";

        final AuthorizationSubject allGrantedSubject = AuthorizationSubject.newInstance("dummy:all-granted");
        final AuthorizationSubject someRevokedSubject = AuthorizationSubject.newInstance("dummy:some-revoked");

        final Permissions permissions = Permissions.newInstance("READ", "WRITE");
        final PolicyId policyId = PolicyId.of("namespace", "id");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("connections-grant")
                .setSubject(allGrantedSubject.getId(), SubjectType.GENERATED)
                .setSubject(someRevokedSubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), permissions)
                .setGrantedPermissions(PoliciesResourceType.policyResource("/"), permissions)
                .setGrantedPermissions(PoliciesResourceType.messageResource("/"), permissions)
                .forLabel("connections-revoke")
                .setSubject(someRevokedSubject.getId(), SubjectType.GENERATED)
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/" + featureId), permissions)
                .build();

        final TrieBasedPolicyEnforcer underTest = TrieBasedPolicyEnforcer.newInstance(policy);

        final Set<AuthorizationSubject> subjects =
                underTest.getSubjectsWithUnrestrictedPermission(ResourceKey.newInstance("thing", "/"), "READ");

        assertThat(subjects)
                .isNotEmpty()
                .doesNotContain(someRevokedSubject);
    }

    @Test
    public void getAccessiblePathsHandlesGrantedNullObject() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("dummy:partial");
        final Policy policy = Policy.newBuilder(PolicyId.of("namespace", "id"))
                .forLabel("partial")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(
                        PoliciesResourceType.thingResource("/features/TestFeature"),
                        Permissions.newInstance("READ"))
                .build();
        final JsonObject thingJson = thingWithNullFeature();
        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final Set<JsonPointer> triePaths = accessiblePaths(
                PolicyEnforcers.throughputOptimizedEvaluator(policy), resourceKey, thingJson, subject);
        final Set<JsonPointer> treePaths = accessiblePaths(
                PolicyEnforcers.memoryOptimizedEvaluator(policy), resourceKey, thingJson, subject);

        assertThat(triePaths).containsExactly(JsonPointer.of("/features/TestFeature"));
        assertThat(triePaths).isEqualTo(treePaths);
    }

    @Test
    public void getAccessiblePathsExcludesGrantedParentWithRevokedChild() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("dummy:partial");
        final Policy policy = Policy.newBuilder(PolicyId.of("namespace", "id"))
                .forLabel("partial")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/features/f1"),
                        Permissions.newInstance("READ"))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/f1/properties/secret"),
                        Permissions.newInstance("READ"))
                .build();
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("f1", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("secret", JsonFactory.newValue("secret-value"))
                                        .set("ok", JsonFactory.newValue("ok-value"))
                                        .build())
                                .build())
                        .build())
                .build();
        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final Set<JsonPointer> triePaths = accessiblePaths(
                PolicyEnforcers.throughputOptimizedEvaluator(policy), resourceKey, thingJson, subject);
        final Set<JsonPointer> treePaths = accessiblePaths(
                PolicyEnforcers.memoryOptimizedEvaluator(policy), resourceKey, thingJson, subject);

        assertThat(triePaths).containsExactly(JsonPointer.of("/features/f1/properties/ok"));
        assertThat(triePaths).isEqualTo(treePaths);
    }

    @Test
    public void getAccessiblePathsReturnsNoPathForNullObjectWithoutGrant() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("dummy:partial");
        final Policy policy = Policy.newBuilder(PolicyId.of("namespace", "id")).build();
        final Set<JsonPointer> accessiblePaths = accessiblePaths(
                PolicyEnforcers.throughputOptimizedEvaluator(policy),
                ResourceKey.newInstance("thing", "/"),
                thingWithNullFeature(),
                subject);

        assertThat(accessiblePaths).isEmpty();
    }

    @Test
    public void getAccessiblePathsFindsNullObjectUnderGrantedAncestor() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("dummy:partial");
        final Policy policy = Policy.newBuilder(PolicyId.of("namespace", "id"))
                .forLabel("partial")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/features"),
                        Permissions.newInstance("READ"))
                .build();
        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final JsonObject thingJson = thingWithNullFeature();
        final Set<JsonPointer> accessiblePaths = accessiblePaths(
                PolicyEnforcers.throughputOptimizedEvaluator(policy), resourceKey, thingJson, subject);
        final Set<JsonPointer> treePaths = accessiblePaths(
                PolicyEnforcers.memoryOptimizedEvaluator(policy), resourceKey, thingJson, subject);

        assertThat(accessiblePaths).isEqualTo(treePaths);
        assertThat(accessiblePaths).containsExactly(JsonPointer.of("/features/TestFeature"));
    }

    @Test
    public void getAccessiblePathsExcludesRevokedNullObjectPathBelowGrantedAncestor() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("dummy:partial");
        final Policy policy = Policy.newBuilder(PolicyId.of("namespace", "id"))
                .forLabel("partial")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/features"),
                        Permissions.newInstance("READ"))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/TestFeature"),
                        Permissions.newInstance("READ"))
                .build();
        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final JsonObject thingJson = thingWithNullFeature();
        final Set<JsonPointer> accessiblePaths = accessiblePaths(
                PolicyEnforcers.throughputOptimizedEvaluator(policy), resourceKey, thingJson, subject);
        final Set<JsonPointer> treePaths = accessiblePaths(
                PolicyEnforcers.memoryOptimizedEvaluator(policy), resourceKey, thingJson, subject);

        assertThat(accessiblePaths).isEqualTo(treePaths);
        assertThat(accessiblePaths).isEmpty();
    }

    @Test
    public void getAccessiblePathsReturnsNoPathForNullObjectAtResourceRoot() {
        final AuthorizationSubject subject = AuthorizationSubject.newInstance("dummy:partial");
        final Policy policy = Policy.newBuilder(PolicyId.of("namespace", "id"))
                .forLabel("partial")
                .setSubject(subject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/features/TestFeature"),
                        Permissions.newInstance("READ"))
                .build();
        final JsonPointer resourcePath = JsonPointer.of("/features/TestFeature");
        final Set<JsonPointer> accessiblePaths = accessiblePaths(
                PolicyEnforcers.throughputOptimizedEvaluator(policy),
                ResourceKey.newInstance("thing", resourcePath),
                JsonFactory.nullObject(),
                subject);

        assertThat(accessiblePaths).isEmpty();
    }

    private static Policy defaultPolicy(final PolicyId policyId) {
        final Permissions permissions = Permissions.newInstance("READ", "WRITE");
        return PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("DEFAULT")
                .setSubject("dummy:test", SubjectType.GENERATED)
                .setGrantedPermissions("foo", JsonPointer.of("/foo"), permissions)
                .setRevision(1L)
                .build();
    }

    private static Set<JsonPointer> accessiblePaths(final Enforcer enforcer,
            final ResourceKey resourceKey,
            final JsonObject thingJson,
            final AuthorizationSubject subject) {
        final AuthorizationContext authorizationContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, subject);
        return enforcer.getAccessiblePaths(resourceKey, thingJson, authorizationContext,
                Permissions.newInstance("READ"));
    }

    private static JsonObject thingWithNullFeature() {
        return JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("TestFeature", JsonFactory.nullObject())
                        .build())
                .build();
    }
}
