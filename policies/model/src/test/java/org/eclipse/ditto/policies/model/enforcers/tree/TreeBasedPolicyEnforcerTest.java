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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.SubjectType;
import org.junit.Test;

/**
 * Unit test for {@link TreeBasedPolicyEnforcer}.
 *
 * <em>The actual unit tests are located in TreeBasedPolicyAlgorithmTest of module policies-service.</em>
 */
public final class TreeBasedPolicyEnforcerTest {

    @Test
    public void tryToCreateInstanceWithNullPolicy() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TreeBasedPolicyEnforcer.createInstance(null))
                .withMessage("The %s must not be null!", "policyEntries")
                .withNoCause();
    }

    @Test
    public void buildJsonViewForNullValue() {
        final PolicyId policyId = PolicyId.of("namespace", "id");
        final TreeBasedPolicyEnforcer underTest =
                TreeBasedPolicyEnforcer.createInstance(Policy.newBuilder(policyId).build());

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
    public void getSubjectsWithRevokePermission() {
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

        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy);

        final Set<AuthorizationSubject> subjects =
                underTest.getSubjectsWithUnrestrictedPermission(ResourceKey.newInstance("thing", "/"), "READ");

        assertThat(subjects)
                .isNotEmpty()
                .doesNotContain(someRevokedSubject);
    }

    @Test
    public void getAccessiblePathsExcludesParentPathsWithRevokedChildren() {
        final AuthorizationSubject partialSubject = AuthorizationSubject.newInstance("test:partial");

        final PolicyId policyId = PolicyId.of("namespace", "id");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("partial")
                .setSubject(partialSubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/complex/some"), Permissions.newInstance("READ"))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/complex/secret"), Permissions.newInstance("READ"))
                .build();

        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy);

        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("complex", JsonFactory.newObjectBuilder()
                                .set("some", JsonFactory.newValue(42))
                                .set("secret", JsonFactory.newValue("secret-value"))
                                .set("other", JsonFactory.newValue("other-value"))
                                .build())
                        .set("type", JsonFactory.newValue("LORAWAN_GATEWAY"))
                        .build())
                .build();

        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final AuthorizationContext authContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, partialSubject);

        final Set<JsonPointer> accessiblePaths = underTest.getAccessiblePaths(
                resourceKey,
                thingJson,
                authContext,
                Permissions.newInstance("READ"));

        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/complex/some"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/complex"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/complex/secret"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/type"));
    }

    @Test
    public void getAccessiblePathsExcludesParentPathsWithMultipleRevokedChildren() {
        final AuthorizationSubject partialSubject = AuthorizationSubject.newInstance("test:partial");

        final PolicyId policyId = PolicyId.of("namespace", "id");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("partial")
                .setSubject(partialSubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/complex/some"), Permissions.newInstance("READ"))
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/complex/other"), Permissions.newInstance("READ"))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/complex/secret"), Permissions.newInstance("READ"))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/complex/hidden"), Permissions.newInstance("READ"))
                .build();

        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy);

        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("complex", JsonFactory.newObjectBuilder()
                                .set("some", JsonFactory.newValue(42))
                                .set("other", JsonFactory.newValue("other-value"))
                                .set("secret", JsonFactory.newValue("secret-value"))
                                .set("hidden", JsonFactory.newValue("hidden-value"))
                                .build())
                        .build())
                .build();

        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final AuthorizationContext authContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, partialSubject);

        final Set<JsonPointer> accessiblePaths = underTest.getAccessiblePaths(
                resourceKey,
                thingJson,
                authContext,
                Permissions.newInstance("READ"));

        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/complex/some"));
        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/complex/other"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/complex"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/complex/secret"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/complex/hidden"));
    }

    @Test
    public void getAccessiblePathsIncludesParentPathWhenNoChildrenRevoked() {
        final AuthorizationSubject fullSubject = AuthorizationSubject.newInstance("test:full");

        final PolicyId policyId = PolicyId.of("namespace", "id");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("full")
                .setSubject(fullSubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/complex"), Permissions.newInstance("READ"))
                .build();

        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy);

        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("complex", JsonFactory.newObjectBuilder()
                                .set("some", JsonFactory.newValue(42))
                                .set("other", JsonFactory.newValue("other-value"))
                                .build())
                        .build())
                .build();

        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final AuthorizationContext authContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, fullSubject);

        final Set<JsonPointer> accessiblePaths = underTest.getAccessiblePaths(
                resourceKey,
                thingJson,
                authContext,
                Permissions.newInstance("READ"));

        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/complex/some"));
        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/complex/other"));
    }

    @Test
    public void getAccessiblePathsHandlesNestedRevokedPaths() {
        final AuthorizationSubject partialSubject = AuthorizationSubject.newInstance("test:partial");

        final PolicyId policyId = PolicyId.of("namespace", "id");
        final Policy policy = Policy.newBuilder(policyId)
                .forLabel("partial")
                .setSubject(partialSubject.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"), Permissions.newInstance("READ"))
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/type"), Permissions.newInstance("READ"))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/hidden"), Permissions.newInstance("READ"))
                .build();

        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy);

        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("type", JsonFactory.newValue("LORAWAN_GATEWAY"))
                        .set("hidden", JsonFactory.newValue(false))
                        .set("complex", JsonFactory.newObjectBuilder()
                                .set("some", JsonFactory.newValue(42))
                                .build())
                        .build())
                .build();

        final ResourceKey resourceKey = ResourceKey.newInstance("thing", "/");
        final AuthorizationContext authContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, partialSubject);

        final Set<JsonPointer> accessiblePaths = underTest.getAccessiblePaths(
                resourceKey,
                thingJson,
                authContext,
                Permissions.newInstance("READ"));

        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/type"));
        assertThat(accessiblePaths).contains(JsonPointer.of("/attributes/complex/some"));
        assertThat(accessiblePaths).doesNotContain(JsonPointer.of("/attributes/hidden"));
    }

}
