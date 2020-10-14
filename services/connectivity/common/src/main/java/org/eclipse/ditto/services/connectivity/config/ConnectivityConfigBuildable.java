/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.config;

/**
 * Provides a method to build a new {@link ConnectivityConfig} from a given (e.g. the fallback/default) one.
 */
public interface ConnectivityConfigBuildable {

    /**
     * Implementations of this method build a new instance of a {@link ConnectivityConfig} from data available to the
     * implementation and the given (default/fallback) {@link ConnectivityConfig}.
     *
     * @param connectivityConfig an existing {@link ConnectivityConfig}
     * @return new instance of {@link ConnectivityConfig}
     */
    ConnectivityConfig buildWith(ConnectivityConfig connectivityConfig);

}
