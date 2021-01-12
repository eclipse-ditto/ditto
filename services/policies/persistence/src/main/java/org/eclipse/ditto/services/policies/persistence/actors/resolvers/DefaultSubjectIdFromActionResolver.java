/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.resolvers;

import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.policies.actions.PolicyActionCommand;

/**
 * Default subject ID resolver utilizing the {@link org.eclipse.ditto.model.placeholders.PolicyEntryPlaceholder}.
 */
@SuppressWarnings("unused") // called by reflection
public final class DefaultSubjectIdFromActionResolver implements SubjectIdFromActionResolver {

    public DefaultSubjectIdFromActionResolver() {
        // no-op
    }

    @Override
    public SubjectId resolveSubjectId(final PolicyEntry entry, final PolicyActionCommand<?> command) {
        return resolveSubjectId(entry, command.getSubjectId());
    }

    SubjectId resolveSubjectId(final PolicyEntry entry, final SubjectId subjectIdWithPlaceholder) {
        return PlaceholderFactory.newExpressionResolver(PlaceholderFactory.newPlaceholderResolver(
                PlaceholderFactory.newPolicyEntryPlaceholder(),
                entry)
        )
                .resolve(subjectIdWithPlaceholder.toString())
                .toOptional()
                .map(SubjectId::newInstance)
                .orElseThrow(() ->
                        UnresolvedPlaceholderException.newBuilder(subjectIdWithPlaceholder.toString()).build());
    }

}
