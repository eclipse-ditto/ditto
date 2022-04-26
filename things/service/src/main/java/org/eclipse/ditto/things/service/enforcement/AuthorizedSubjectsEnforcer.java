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
package org.eclipse.ditto.things.service.enforcement;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.DefaultEffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;

/**
 * An enforcer that grants access to a set of subjects and no access to everything else.
 */
final class AuthorizedSubjectsEnforcer implements Enforcer {

    private final AuthorizationContext authorizationContext;

    AuthorizedSubjectsEnforcer(final AuthorizationContext authorizationContext) {
        this.authorizationContext = authorizationContext;
    }

    @Override
    public boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext, final Permissions permissions) {

        final List<AuthorizationSubject> authorizationSubjects = this.authorizationContext.getAuthorizationSubjects();
        return authorizationContext.stream().anyMatch(authorizationSubjects::contains);
    }

    @Override
    public EffectedSubjects getSubjectsWithPermission(final ResourceKey resourceKey, final Permissions permissions) {
        return DefaultEffectedSubjects.of(authorizationContext.getAuthorizationSubjects(), Collections.emptyList());
    }

    @Override
    public Set<AuthorizationSubject> getSubjectsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        return new HashSet<>(authorizationContext.getAuthorizationSubjects());
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey, final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        return hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions);
    }

    @Override
    public Set<AuthorizationSubject> getSubjectsWithUnrestrictedPermission(final ResourceKey resourceKey,
            final Permissions permissions) {
        return new HashSet<>(authorizationContext.getAuthorizationSubjects());
    }

    @Override
    public JsonObject buildJsonView(final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        return hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions)
                ? JsonFactory.newObjectBuilder(jsonFields).build()
                : JsonFactory.newObject();
    }

}
