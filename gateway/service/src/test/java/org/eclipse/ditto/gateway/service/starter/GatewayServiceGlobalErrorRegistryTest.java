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
package org.eclipse.ditto.gateway.service.starter;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityIdInvalidException;
import org.eclipse.ditto.base.model.exceptions.CloudEventMissingPayloadException;
import org.eclipse.ditto.base.model.exceptions.CloudEventNotParsableException;
import org.eclipse.ditto.base.model.exceptions.CloudEventUnsupportedDataSchemaException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.PathUnknownException;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionConflictException;
import org.eclipse.ditto.edge.service.EdgeServiceTimeoutException;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.PublicKeyProviderUnavailableException;
import org.eclipse.ditto.internal.utils.test.GlobalErrorRegistryTestCases;
import org.eclipse.ditto.jwt.model.JwtAudienceInvalidException;
import org.eclipse.ditto.messages.model.AuthorizationSubjectBlockedException;
import org.eclipse.ditto.placeholders.PlaceholderFunctionSignatureInvalidException;
import org.eclipse.ditto.policies.model.PolicyEntryInvalidException;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.protocol.mappingstrategies.IllegalAdaptableException;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.eclipse.ditto.thingsearch.api.QueryTimeExceededException;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidNamespacesException;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;

public final class GatewayServiceGlobalErrorRegistryTest extends GlobalErrorRegistryTestCases {

    public GatewayServiceGlobalErrorRegistryTest() {
        super(
                UnknownCommandException.class,
                DittoHeaderInvalidException.class,
                PolicyEntryInvalidException.class,
                AttributePointerInvalidException.class,
                CommandNotSupportedException.class,
                UnsupportedSchemaVersionException.class,
                UnsupportedSignalException.class,
                GatewayAuthenticationFailedException.class,
                QueryTimeExceededException.class,
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
                UnknownTopicPathException.class,
                IllegalAdaptableException.class,
                WotThingModelInvalidException.class,
                EdgeServiceTimeoutException.class
        );
    }

}
