/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base;

import javax.annotation.concurrent.Immutable;

/**
 * This marker interface provides enumerations of commonly known configuration keys. <em>This interface should not
 * be implemented by anyone.</em>
 */
@Immutable
public interface BaseConfigKey {

    /**
     * Enumeration of keys for cluster configuration settings.
     */
    enum Cluster implements BaseConfigKey {

        /**
         * Key of the configuration setting which indicates whether the majority check is enabled.
         */
        MAJORITY_CHECK_ENABLED,

        /**
         * Key of the majority check delay configuration setting.
         */
        MAJORITY_CHECK_DELAY;

    }

    /**
     * Enumeration of keys for StatsD configuration settings.
     */
    enum StatsD implements BaseConfigKey {

        /**
         * Key of the StatsD hostname configuration setting.
         */
        HOSTNAME,

        /**
         * Key of the StatsD port configuration setting.
         */
        PORT;

    }

}
