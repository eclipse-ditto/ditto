/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

/**
 * Extension for creating a {@link PayloadMapper}.
 */
public interface PayloadMapperFactory extends DittoExtensionPoint {

    PayloadMapper createPayloadMapper();

    String getPayloadMapperAlias();

    /**
     * @return {@code true} if the mapper requires mandatory {@code config} options for initialization,
     * i.e. it cannot be used directly as a mapping without providing the
     * {@link org.eclipse.ditto.connectivity.model.MappingContext#getOptionsAsJson()}.
     */
    default boolean isConfigurationMandatory() {
        return false;
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<PayloadMapperFactory> {

        ExtensionId(final ExtensionIdConfig<PayloadMapperFactory> extensionIdConfig) {
            super(extensionIdConfig);
        }

        @Override
        protected String getConfigKey() {
            throw new UnsupportedOperationException("PayloadMappers do not support an individual config key. " +
                    "They should be configured in the ditto.extensions.payload-mapper-provider.extension-config " +
                    "payload-mappers list.");
        }

    }

}
