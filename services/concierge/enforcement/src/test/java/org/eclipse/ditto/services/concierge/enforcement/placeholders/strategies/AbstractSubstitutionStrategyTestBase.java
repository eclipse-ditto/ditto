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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution;
import org.junit.Before;
import org.junit.Test;

/**
 * Abstract base class for test of concrete implementations of {@link SubstitutionStrategy}.
 */
public abstract class AbstractSubstitutionStrategyTestBase {

    protected static final String SUBJECT_ID_PLACEHOLDER = "{{ request:subjectId }}";

    private static final String NAMESPACE = "org.eclipse.ditto";
    protected static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, "my-policy");
    protected static final String LABEL = "my-label";
    protected static final String LABEL_2 = "my-label-2";
    protected static final String SUBJECT_ID = "nginx:ditto";
    protected static final String SUBJECT_ID_2 = "nginx:ditto2";
    protected static final Iterable<Resource> RESOURCES = Collections.singleton(
            Resource.newInstance("resourceKey", "resourcePath",
            EffectedPermissions.newInstance(Collections.singleton("READ"), Collections.emptySet())));

    protected static final ThingId THING_ID = ThingId.of(NAMESPACE, "my-thing");
    protected static final Thing THING = Thing.newBuilder().setId(THING_ID)
            .setAttributes(JsonObject.newBuilder().set("key", "val").build())
            .build();

    protected static final Iterable<Permission> ACL_PERMISSIONS = Arrays.asList(Permission.READ, Permission.WRITE);

    protected static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(SUBJECT_ID)))
            .build();

    protected static final DittoHeaders DITTO_HEADERS_V1 = DittoHeaders.newBuilder(DITTO_HEADERS)
            .schemaVersion(JsonSchemaVersion.V_1)
            .build();

    protected PlaceholderSubstitution substitution;

    @Before
    public void init() {
        substitution = PlaceholderSubstitution.newInstance();
    }

    @Test
    public abstract void assertImmutability();

    protected final WithDittoHeaders applyBlocking(final WithDittoHeaders input) {
        final CompletionStage<WithDittoHeaders> responseFuture = substitution.apply(input);
        try {
            return responseFuture.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
