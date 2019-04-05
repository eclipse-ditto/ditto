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
package org.eclipse.ditto.services.utils.cluster;

import java.util.Map;

public final class DefaultMappingStrategies extends AbstractMappingStrategies {

    private DefaultMappingStrategies(final Map<String, MappingStrategy> strategies) {
        super(strategies);
    }

    public static DefaultMappingStrategies of(final Map<String, MappingStrategy> strategies) {
        return new DefaultMappingStrategies(strategies);
    }

}
