/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.resolvers;

import java.util.Set;

import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand;

/**
 * Adds ability to resolve a Policy {@link SubjectId} from a {@link PolicyActionCommand}.
 */
public interface SubjectIdFromActionResolver {

    /**
     * Resolves subject IDs.
     *
     * @param entry the policy entry.
     * @param command the policy action command.
     * @return the subject IDs after resolution.
     * @throws org.eclipse.ditto.placeholders.UnresolvedPlaceholderException if one of the subject IDs contains
     * unsupported placeholders.
     * @throws org.eclipse.ditto.policies.model.SubjectIdInvalidException if one of the resolved subject IDs is invalid.
     */
    Set<SubjectId> resolveSubjectIds(PolicyEntry entry, PolicyActionCommand<?> command);
}
