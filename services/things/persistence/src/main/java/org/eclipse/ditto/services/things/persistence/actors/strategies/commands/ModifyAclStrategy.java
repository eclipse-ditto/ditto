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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link ModifyAcl} command.
 */
@Immutable
public final class ModifyAclStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyAcl, AccessControlList> {

    /**
     * Constructs a new {@code ModifyAclStrategy} object.
     */
    ModifyAclStrategy() {
        super(ModifyAcl.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAcl command) {
        final ThingId thingId = context.getState();
        final AccessControlList newAccessControlList = command.getAccessControlList();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Validator aclValidator = AclValidator.newInstance(newAccessControlList,
                Thing.MIN_REQUIRED_PERMISSIONS);
        if (!aclValidator.isValid()) {
            return ResultFactory.newErrorResult(ExceptionFactory.aclInvalid(thingId, aclValidator.getReason(),
                    dittoHeaders));
        }

        final ThingEvent event =
                AclModified.of(thingId, newAccessControlList, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAclResponse.modified(thingId, newAccessControlList, command.getDittoHeaders()), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<AccessControlList> determineETagEntity(final ModifyAcl command, @Nullable final Thing thing) {
        return Optional.of(command.getAccessControlList());
    }
}
