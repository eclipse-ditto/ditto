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

import java.text.MessageFormat;
import java.time.Duration;

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

    /**
     * The {@code ditto} "root" scope used in most of the configurations in Eclipse Ditto.
     */
    String DITTO_SCOPE = "ditto";

    /**
     * Same as {@link #getDuration(String)} but with the guarantee that the returned Duration is non-negative.
     *
     * @param withConfigPath provides the config path to get the Duration value for.
     * @return the duration.
     * @throws DittoConfigError if the Duration at the config path is negative.
     */
    default Duration getNonNegativeDurationOrThrow(final WithConfigPath withConfigPath) {
        final Duration result = getDuration(withConfigPath.getConfigPath());
        if (result.isNegative()) {
            final String msgPattern = "The duration at <{0}> must not be negative but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getInt(String)} but with the guarantee that the returned value is positive.
     *
     * @param withConfigPath provides the config path to get the int value for.
     * @return the int value.
     * @throws DittoConfigError if the int value at the config path is zero or negative.
     */
    default int getPositiveIntOrThrow(final WithConfigPath withConfigPath) {
        final int result = getInt(withConfigPath.getConfigPath());
        if (1 > result) {
            final String msgPattern = "The int value at <{0}> must be positive but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

}
