/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.enforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.ImmutableEffectedSubjectIds;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * An enforcer that grants access to a set of subjects and no access to everything else.
 */
@AllValuesAreNonnullByDefault
final class AuthorizedSubjectsEnforcer implements Enforcer {

    private final Set<String> authorizedSubjects;

    AuthorizedSubjectsEnforcer(final Set<String> authorizedSubjects) {
        this.authorizedSubjects = authorizedSubjects;
    }


    @Override
    public boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {
        return authorizationContext.getAuthorizationSubjects()
                .stream()
                .anyMatch(subject -> authorizedSubjects.contains(subject.getId()));
    }

    @Override
    public EffectedSubjectIds getSubjectIdsWithPermission(final ResourceKey resourceKey,
            final Permissions permissions) {
        return ImmutableEffectedSubjectIds.of(authorizedSubjects, Collections.emptySet());
    }

    @Override
    public Set<String> getSubjectIdsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {
        return authorizedSubjects;
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey, final AuthorizationContext authorizationContext,
            final Permissions permissions) {
        return hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions);
    }

    @Override
    public JsonObject buildJsonView(final ResourceKey resourceKey, final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext, final Permissions permissions) {
        return hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions)
                ? JsonFactory.newObjectBuilder(jsonFields).build()
                : JsonFactory.newObject();
    }
}
