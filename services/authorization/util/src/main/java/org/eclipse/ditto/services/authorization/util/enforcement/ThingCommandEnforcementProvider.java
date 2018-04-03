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
package org.eclipse.ditto.services.authorization.util.enforcement;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * Provides {@link Enforcement} for commands of type {@link ThingCommand}.
 */
public final class ThingCommandEnforcementProvider implements EnforcementProvider<ThingCommand> {

    private static final List<SubjectIssuer> SUBJECT_ISSUERS_FOR_POLICY_MIGRATION =
            Collections.singletonList(SubjectIssuer.GOOGLE);

    @Override
    public Class<ThingCommand> getCommandClass() {
        return ThingCommand.class;
    }

    @Override
    public boolean isApplicable(final ThingCommand command) {
        // live commands are not applicable for thing command enforcement
        // because they should never be forwarded to things shard region
        return !LiveSignalEnforcement.isLiveSignal(command);
    }

    @Override
    public Enforcement<ThingCommand> createEnforcement(final Enforcement.Context context) {
        return new ThingCommandEnforcement(context, SUBJECT_ISSUERS_FOR_POLICY_MIGRATION);
    }
}
