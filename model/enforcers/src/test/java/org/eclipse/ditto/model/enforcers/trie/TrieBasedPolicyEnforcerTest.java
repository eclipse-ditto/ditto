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
package org.eclipse.ditto.model.enforcers.trie;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.SubjectType;
import org.junit.Test;

public class TrieBasedPolicyEnforcerTest {

    @Test
    public void buildJsonView() {
        final TrieBasedPolicyEnforcer underTest =
                TrieBasedPolicyEnforcer.newInstance(defaultPolicy(PolicyId.of("namespace", "id")));

        final JsonObject createdJsonView = underTest.buildJsonView(
                ResourceKey.newInstance("foo", "bar"),
                JsonFactory.nullObject(),
                AuthorizationContext.newInstance(AuthorizationSubject.newInstance("itsMe")),
                Permissions.none());

        final JsonObject expectedJsonView = JsonFactory.nullObject();

        assertThat(createdJsonView).isEqualTo(expectedJsonView);
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
}
