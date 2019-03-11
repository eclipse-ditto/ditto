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
package org.eclipse.ditto.services.gateway.starter;

import java.util.Arrays;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.messages.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.model.placeholders.PlaceholderFunctionSignatureInvalidException;
import org.eclipse.ditto.model.policies.PolicyEntryInvalidException;
import org.eclipse.ditto.model.things.AclEntryInvalidException;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.services.utils.test.GlobalErrorRegistryTestCases;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.batch.exceptions.BatchAlreadyExecutingException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidNamespacesException;

public final class GatewayServiceGlobalErrorRegistryTest extends GlobalErrorRegistryTestCases {

    public GatewayServiceGlobalErrorRegistryTest() {
        super(Arrays.asList(
                UnknownCommandException.class,
                DittoHeaderInvalidException.class,
                PolicyEntryInvalidException.class,
                AclEntryInvalidException.class,
                CommandNotSupportedException.class,
                GatewayAuthenticationFailedException.class,
                ConnectionConflictException.class,
                ConnectionConfigurationInvalidException.class,
                PolicyConflictException.class,
                AclModificationInvalidException.class,
                AuthorizationSubjectBlockedException.class,
                JsonTypeNotParsableException.class,
                BatchAlreadyExecutingException.class,
                InvalidNamespacesException.class,
                NamespaceBlockedException.class,
                PlaceholderFunctionSignatureInvalidException.class
        ));
    }
}
