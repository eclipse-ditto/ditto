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
package org.eclipse.ditto.services.utils.config;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;

/**
 * This extension of {@link com.typesafe.config.Config} knows the path which led to itself.
 * Based on the following example config
 * <pre>
 *    ditto {
 *      concierge {
 *        caches {
 *          ask-timeout = 10s
 *
 *          id {
 *            maximum-size = 80000
 *            expire-after-write = 15m
 *          }
 *
 *          enforcer {
 *            maximum-size = 20000
 *            expire-after-write = 15m
 *          }
 *        }
 *
 *        mongodb {
 *          uri = "mongodb://localhost:27017/concierge"
 *        }
 *      }
 *    }
 * </pre>
 * a {@code ScopedConfig} with config path {@code caches} would comprise the following:
 * <pre>
 *    {
 *      ask-timeout = 10s
 *
 *      id {
 *        maximum-size = 80000
 *        expire-after-write = 15m
 *      }
 *
 *      enforcer {
 *        maximum-size = 20000
 *        expire-after-write = 15m
 *      }
 *    }
 * </pre>
 * A call to method {@link #getConfigPath()} would return {@code "ditto.concierge.caches"}.
 * <p>
 *   All get methods will throw a {@link DittoConfigError} if the config at the particular path is missing the value or
 *   if the value has a wrong type.
 * </p>
 */
@Immutable
public interface ScopedConfig extends Config, WithConfigPath {
}
