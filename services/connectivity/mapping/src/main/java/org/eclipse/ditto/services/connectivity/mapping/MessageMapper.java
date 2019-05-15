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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

/**
 * Defines a message mapper which maps a {@link ExternalMessage} to a {@link Adaptable} and vice versa.
 * <p>
 * Usually a mapper is bound to a content type.
 * </p>
 * A message mapper is considered to be dynamically instantiated at runtime, it therefore can only be configured at
 * runtime.
 */
public interface MessageMapper {

    /**
     * Applies configuration for this MessageMapper.
     *
     * @param mappingConfig the config scoped to the mapping section "ditto.connectivity.mapping".
     * @param configuration the configuration to apply.
     * @throws MessageMapperConfigurationInvalidException if configuration is invalid.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration
     * failed for a mapper specific reason.
     */
    void configure(MappingConfig mappingConfig, MessageMapperConfiguration configuration);

    /**
     * Returns the content type of this mapper. This can be used as a hint for mapper selection.
     *
     * @return the content type
     */
    default Optional<String> getContentType() {
        return Optional.empty();
    }

    /**
     * Maps an {@link ExternalMessage} to an {@link Adaptable}
     *
     * @param message the ExternalMessage to map
     * @return the mapped Adaptable or an empty Optional if the ExternalMessage should not be mapped after all
     * @throws org.eclipse.ditto.model.connectivity.MessageMappingFailedException if the given message can not be mapped
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if anything during Ditto Adaptable creation
     * went wrong
     */
    Optional<Adaptable> map(ExternalMessage message);

    /**
     * Maps an {@link Adaptable} to an {@link ExternalMessage}
     *
     * @param adaptable the Adaptable to map
     * @return the ExternalMessage or an empty Optional if the Adaptable should not be mapped after all
     * @throws org.eclipse.ditto.model.connectivity.MessageMappingFailedException if the given adaptable can not be mapped
     */
    Optional<ExternalMessage> map(Adaptable adaptable);

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
        return adaptable.getHeaders().map(h -> h.entrySet().stream()
                .filter(e -> ExternalMessage.CONTENT_TYPE_HEADER.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue).orElse(null));
    }
}
