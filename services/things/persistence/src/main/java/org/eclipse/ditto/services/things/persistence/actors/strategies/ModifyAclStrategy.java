/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.events.things.AclModified;

/**
 * This strategy handles the {@link ModifyAcl} command.
 */
@NotThreadSafe
public final class ModifyAclStrategy extends AbstractThingCommandStrategy<ModifyAcl> {

    /**
     * Constructs a new {@code ModifyAclStrategy} object.
     */
    public ModifyAclStrategy() {
        super(ModifyAcl.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyAcl command) {
        final String thingId = context.getThingId();
        final long nextRevision = context.nextRevision();
        final AccessControlList newAccessControlList = command.getAccessControlList();
        final Validator aclValidator = AclValidator.newInstance(newAccessControlList,
                Thing.MIN_REQUIRED_PERMISSIONS);
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (aclValidator.isValid()) {
            final AclModified aclModified = AclModified.of(thingId, newAccessControlList, nextRevision,
                    eventTimestamp(), dittoHeaders);

            return result(aclModified,
                    ModifyAclResponse.modified(thingId, newAccessControlList, command.getDittoHeaders()));
        } else {
            return result(aclInvalid(thingId, aclValidator.getReason(), dittoHeaders.getAuthorizationContext(),
                    dittoHeaders));
        }
    }

}
