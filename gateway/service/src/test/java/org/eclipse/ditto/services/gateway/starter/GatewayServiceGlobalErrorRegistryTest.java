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
package org.eclipse.ditto.services.gateway.starter;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.base.model.exceptions.CloudEventMissingPayloadException;
import org.eclipse.ditto.base.model.exceptions.CloudEventNotParsableException;
import org.eclipse.ditto.base.model.exceptions.CloudEventUnsupportedDataSchemaException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.jwt.JwtAudienceInvalidException;
import org.eclipse.ditto.model.messages.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.services.models.placeholders.PlaceholderFunctionSignatureInvalidException;
import org.eclipse.ditto.policies.model.PolicyEntryInvalidException;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.PublicKeyProviderUnavailableException;
import org.eclipse.ditto.services.utils.test.GlobalErrorRegistryTestCases;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.PathUnknownException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidNamespacesException;

public final class GatewayServiceGlobalErrorRegistryTest extends GlobalErrorRegistryTestCases {

    public GatewayServiceGlobalErrorRegistryTest() {
        super(UnknownCommandException.class,
                DittoHeaderInvalidException.class,
                PolicyEntryInvalidException.class,
                AttributePointerInvalidException.class,
                CommandNotSupportedException.class,
                UnsupportedSchemaVersionException.class,
                UnsupportedSignalException.class,
                GatewayAuthenticationFailedException.class,
                ConnectionConflictException.class,
                ConnectionConfigurationInvalidException.class,
                PolicyConflictException.class,
                AuthorizationSubjectBlockedException.class,
                JsonTypeNotParsableException.class,
                InvalidNamespacesException.class,
                NamespaceBlockedException.class,
                PlaceholderFunctionSignatureInvalidException.class,
                JwtAudienceInvalidException.class,
                NamespacedEntityIdInvalidException.class,
                ThingIdInvalidException.class,
                PolicyIdInvalidException.class,
                PublicKeyProviderUnavailableException.class,
                AcknowledgementLabelInvalidException.class,
                AcknowledgementCorrelationIdMissingException.class,
                CloudEventMissingPayloadException.class,
                CloudEventUnsupportedDataSchemaException.class,
                CloudEventNotParsableException.class,
                PathUnknownException.class,
                UnknownTopicPathException.class
        );
    }

}
