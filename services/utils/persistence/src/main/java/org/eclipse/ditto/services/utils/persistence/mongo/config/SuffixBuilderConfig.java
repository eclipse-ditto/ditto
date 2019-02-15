/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the configuration settings of the MongoDB suffix builder for the Akka persistence plugin.
 */
@Immutable
public interface SuffixBuilderConfig {

    /**
     * Provides the supported prefixes of MongoDB collection name suffixes.
     *
     * @return the supported prefixes.
     */
    List<String> getSupportedPrefixes();

}
