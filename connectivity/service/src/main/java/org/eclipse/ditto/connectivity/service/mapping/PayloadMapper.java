/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

/**
 * Classes implementing {@link PayloadMapper} are loaded on startup to be referenced by its alias in payload mapping
 * definitions of a {@link org.eclipse.ditto.connectivity.model.Connection}. If the mapper requires no
 * {@link org.eclipse.ditto.connectivity.model.MappingContext} for initialization it can also be directly used in the
 * list of mappings of a {@link org.eclipse.ditto.connectivity.model.Source} or a
 * {@link org.eclipse.ditto.connectivity.model.Target} using one of the defined aliases.
 */
public interface PayloadMapper {

    /**
     * @return the alias to reference this {@link PayloadMapper}.
     */
    String getAlias();

    default int getPriority() {
        return 0;
    }

}
