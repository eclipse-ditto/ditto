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
package org.eclipse.ditto.policies.service.starter;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.PathUnknownException;
import org.eclipse.ditto.internal.utils.test.GlobalErrorRegistryTestCases;
import org.eclipse.ditto.placeholders.PlaceholderFunctionUnknownException;
import org.eclipse.ditto.policies.model.PolicyEntryInvalidException;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportNotAccessibleException;

public final class PoliciesServiceGlobalErrorRegistryTest extends GlobalErrorRegistryTestCases {

    public PoliciesServiceGlobalErrorRegistryTest() {
        super(
                DittoHeaderInvalidException.class,
                PolicyEntryInvalidException.class,
                CommandNotSupportedException.class,
                UnsupportedSchemaVersionException.class,
                UnsupportedSignalException.class,
                DittoInternalErrorException.class,
                CommandTimeoutException.class,
                PolicyConflictException.class,
                JsonTypeNotParsableException.class,
                NamespaceBlockedException.class,
                NamespacedEntityIdInvalidException.class,
                PolicyIdInvalidException.class,
                AcknowledgementLabelInvalidException.class,
                AcknowledgementCorrelationIdMissingException.class,
                PathUnknownException.class,
                PolicyImportNotAccessibleException.class,

                // added due to ditto-model-placeholders
                PlaceholderFunctionUnknownException.class
        );
    }

}
