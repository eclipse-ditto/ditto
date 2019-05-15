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
 * <p>
 *   Java serialization is supported for {@code ScopedConfig}.
 * </p>
 */
@Immutable
public interface ScopedConfig extends Config, WithConfigPath {
}
