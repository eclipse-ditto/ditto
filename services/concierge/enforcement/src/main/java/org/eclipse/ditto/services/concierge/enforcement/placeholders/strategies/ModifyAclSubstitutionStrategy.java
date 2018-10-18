/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;

/**
 * Handles substitution for ACL {@link AuthorizationSubject}s inside a {@link ModifyAcl} command.
 */
final class ModifyAclSubstitutionStrategy extends AbstractTypedSubstitutionStrategy<ModifyAcl> {

    ModifyAclSubstitutionStrategy() {
        super(ModifyAcl.class);
    }

    @Override
    public WithDittoHeaders apply(final ModifyAcl modifyAcl,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm) {
        requireNonNull(modifyAcl);
        requireNonNull(substitutionAlgorithm);

        final DittoHeaders dittoHeaders = modifyAcl.getDittoHeaders();
        final AccessControlList existingAcl = modifyAcl.getAccessControlList();
        final AccessControlList substitutedAcl =
                substituteAcl(existingAcl, substitutionAlgorithm, dittoHeaders);

        if (existingAcl.equals(substitutedAcl)) {
            return modifyAcl;
        } else {
            return ModifyAcl.of(modifyAcl.getThingId(), substitutedAcl, dittoHeaders);
        }
    }

}
