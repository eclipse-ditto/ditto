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
package org.eclipse.ditto.services.policies.starter;

import java.util.Arrays;

import org.eclipse.ditto.model.base.entity.id.EntityIdInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.messages.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.model.policies.PolicyEntryInvalidException;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.model.things.AclEntryInvalidException;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.services.utils.test.GlobalErrorRegistryTestCases;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;

public final class PoliciesServiceGlobalErrorRegistryTest extends GlobalErrorRegistryTestCases {

    public PoliciesServiceGlobalErrorRegistryTest() {
        super(Arrays.asList(
                DittoHeaderInvalidException.class,
                PolicyEntryInvalidException.class,
                AclEntryInvalidException.class,
                CommandNotSupportedException.class,
                GatewayAuthenticationFailedException.class,
                PolicyConflictException.class,
                AclModificationInvalidException.class,
                AuthorizationSubjectBlockedException.class,
                JsonTypeNotParsableException.class,
                NamespaceBlockedException.class,
                EntityIdInvalidException.class,
                ThingIdInvalidException.class,
                PolicyIdInvalidException.class
        ));
    }
}
