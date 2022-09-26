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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;

import akka.actor.ActorSystem;

/**
 * Defines a message mapper which maps a {@link ExternalMessage} to a {@link Adaptable} and vice versa.
 * <p>
 * Usually a mapper is bound to a content type.
 * </p>
 * A message mapper is considered to be dynamically instantiated at runtime, it therefore can only be configured at
 * runtime.
 * All implementations need to have a constructor which accepts two parameters: ActorSystem and Config.
 */
public interface MessageMapper extends DittoExtensionPoint {

    /**
     * Returns a unique ID of this mapper that can be used in sources and targets to reference this mapper.
     *
     * @return a unique ID of this mapper.
     */
    String getId();

    /**
     * Returns the mapper Alias of this mapper.
     *
     * @return the mapper Alias of this mapper.
     */
    String getAlias();

    /**
     * @return {@code true} if the mapper requires mandatory {@code config} options for initialization,
     * i.e. it cannot be used directly as a mapping without providing the
     * {@link org.eclipse.ditto.connectivity.model.MappingContext#getOptionsAsJson()}.
     */
    boolean isConfigurationMandatory();

    /**
     * Creates a new instance of this mapper, e.g. using a copy constructor of the mapper implementation.
     *
     * @return a newly created instance of the mapper.
     */
    MessageMapper createNewMapperInstance();

    /**
     * Returns a blocklist of content-types which shall not be handled by this message mapper.
     * Is determined from the passed in {@code MessageMapperConfiguration} in
     * {@link #configure(Connection, ConnectivityConfig, MessageMapperConfiguration, ActorSystem)}
     *
     * @return a blocklist of content-types which shall not be handled by this message mapper.
     */
    Collection<String> getContentTypeBlocklist();

    /**
     * Applies configuration for this MessageMapper.
     *
     * @param connection the connection
     * @param connectivityConfig the connectivity config related to the given connection.
     * @param configuration the configuration to apply.
     * @param actorSystem the actor system.
     * @throws MessageMapperConfigurationInvalidException if configuration is invalid.
     * @throws org.eclipse.ditto.connectivity.model.MessageMapperConfigurationFailedException if the configuration
     * failed for a mapper specific reason.
     */
    void configure(Connection connection, ConnectivityConfig connectivityConfig,
            MessageMapperConfiguration configuration, ActorSystem actorSystem);

    /**
     * Maps an {@link ExternalMessage} to an {@link Adaptable}
     *
     * @param message the ExternalMessage to map
     * @return the mapped Adaptable or an empty List if the ExternalMessage should not be mapped after all
     * @throws org.eclipse.ditto.connectivity.model.MessageMappingFailedException if the given message can not be mapped
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if anything during Ditto Adaptable creation
     * went wrong
     */
    List<Adaptable> map(ExternalMessage message);

    /**
     * Lets the mapper implementation calculate additional DittoHeaders to set for an incoming inbound
     * {@code externalMessage}.
     *
     * @return the additional DittoHeaders to inject.
     */
    DittoHeaders getAdditionalInboundHeaders(ExternalMessage message);

    /**
     * Maps an {@link Adaptable} to an {@link ExternalMessage}
     *
     * @param adaptable the Adaptable to map
     * @return the ExternalMessage or an empty List if the Adaptable should not be mapped after all
     * @throws org.eclipse.ditto.connectivity.model.MessageMappingFailedException if the given adaptable can not be mapped
     */
    List<ExternalMessage> map(Adaptable adaptable);

    /**
     * @return a map of default options for this mapper
     */
    default JsonObject getDefaultOptions() {
        return JsonObject.empty();
    }

    /**
     * Returns the conditions to be checked before mapping incoming messages.
     *
     * @return the conditions.
     * @since 1.3.0
     */
    Map<String, String> getIncomingConditions();

    /**
     * Returns the conditions to be checked before mapping outgoing messages.
     *
     * @return the conditions.
     * @since 1.3.0
     */
    Map<String, String> getOutgoingConditions();

    /**
     * Finds the content-type header from the passed ExternalMessage.
     *
     * @param externalMessage the ExternalMessage to look for the content-type header in
     * @return the optional content-type value
     */
    static Optional<String> findContentType(final ExternalMessage externalMessage) {
        checkNotNull(externalMessage);
        return externalMessage.findHeaderIgnoreCase(ExternalMessage.CONTENT_TYPE_HEADER);
    }

    /**
     * Finds the content-type header from the passed Adaptable.
     *
     * @param adaptable the Adaptable to look for the content-type header in
     * @return the optional content-type value
     */
    static Optional<String> findContentType(final Adaptable adaptable) {
        checkNotNull(adaptable);
        return adaptable.getDittoHeaders().entrySet()
                .stream()
                .filter(e -> ExternalMessage.CONTENT_TYPE_HEADER.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue);
    }

}
