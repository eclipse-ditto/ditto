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
package org.eclipse.ditto.internal.utils.config;

import java.text.MessageFormat;
import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This extension of {@link com.typesafe.config.Config} knows the path which led to itself.
 * Based on the following example config
 * <pre>
 *    ditto {
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
 *          uri = "mongodb://localhost:27017/things"
 *        }
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
 * A call to method {@link #getConfigPath()} would return {@code "ditto.caches"}.
 * <p>
 * All get methods will throw a {@link DittoConfigError} if the config at the particular path is missing the value or
 * if the value has a wrong type.
 * </p>
 */
@Immutable
public interface ScopedConfig extends Config, WithConfigPath {

    /**
     * The {@code ditto} "root" scope used in most of the configurations in Eclipse Ditto.
     */
    String DITTO_SCOPE = "ditto";

    String DITTO_EXTENSIONS_SCOPE = "ditto.extensions";

    static Config getOrEmpty(final Config config, final String path) {
        return config.hasPath(path) ? config.getConfig(path) : ConfigFactory.empty();
    }

    static Config dittoExtension(final Config config) {
        return getOrEmpty(config, DITTO_EXTENSIONS_SCOPE);
    }

    /**
     * Same as {@link #getDuration(String)} but with the guarantee that the returned Duration is positive.
     *
     * @param withConfigPath provides the config path to get the Duration value for.
     * @return the duration.
     * @throws DittoConfigError if the Duration at the config path is negative.
     */
    default Duration getNonNegativeDurationOrThrow(final WithConfigPath withConfigPath) {
        final var result = getDuration(withConfigPath.getConfigPath());
        if (result.isNegative()) {
            final var msgPattern = "The duration at <{0}> must not be negative but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getDuration(String)} but with the guarantee that the returned Duration is non-negative
     * and non-zero.
     *
     * @param withConfigPath provides the config path to get the Duration value for.
     * @return the duration.
     * @throws DittoConfigError if the Duration at the config path is negative or zero.
     */
    default Duration getNonNegativeAndNonZeroDurationOrThrow(final WithConfigPath withConfigPath) {
        final var result = getDuration(withConfigPath.getConfigPath());
        if (result.isNegative() || result.isZero()) {
            final var msgPattern = "The duration at <{0}> must not be negative and not zero but it was <{1}>!";
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
        final var result = getInt(withConfigPath.getConfigPath());
        if (1 > result) {
            final var msgPattern = "The int value at <{0}> must be positive but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getLong(String)} but with the guarantee that the returned value is positive.
     *
     * @param withConfigPath provides the config path to get the long value for.
     * @return the long value.
     * @throws DittoConfigError if the long value at the config path is zero or negative.
     */
    default long getPositiveLongOrThrow(final WithConfigPath withConfigPath) {
        final var result = getLong(withConfigPath.getConfigPath());
        if (1L > result) {
            final var msgPattern = "The long value at <{0}> must be positive but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getDouble(String)} but with the guarantee that the returned value is positive.
     *
     * @param withConfigPath provides the config path to get the double value for.
     * @return the double value.
     * @throws DittoConfigError if the long value at the config path is zero or negative.
     */
    default double getPositiveDoubleOrThrow(final WithConfigPath withConfigPath) {
        final var result = getDouble(withConfigPath.getConfigPath());
        if (0.0 >= result) {
            final var msgPattern = "The double value at <{0}> must be positive but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getInt(String)} but with the guarantee that the returned int is greater zero.
     *
     * @param withConfigPath provides the config path to get the int value for.
     * @return the int value.
     * @throws DittoConfigError if the int value at the config path is negative.
     */
    default int getNonNegativeIntOrThrow(final WithConfigPath withConfigPath) {
        final var result = getInt(withConfigPath.getConfigPath());
        if (0 > result) {
            final var msgPattern = "The int value at <{0}> must not be negative but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getLong(String)} but with the guarantee that the returned long is greater zero.
     *
     * @param withConfigPath provides the config path to get the long value for.
     * @return the long value.
     * @throws DittoConfigError if the long value at the config path is negative.
     */
    default long getNonNegativeLongOrThrow(final WithConfigPath withConfigPath) {
        final var result = getLong(withConfigPath.getConfigPath());
        if (0 > result) {
            final var msgPattern = "The long value at <{0}> must not be negative but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getDouble(String)} but with the guarantee that the returned double is not negative.
     *
     * @param withConfigPath provides the config path to get the double value for.
     * @return the double value.
     * @throws DittoConfigError if the double value at the config path is negative.
     */
    default double getNonNegativeDoubleOrThrow(final WithConfigPath withConfigPath) {
        final var result = getDouble(withConfigPath.getConfigPath());
        if (0.0 > result) {
            final var msgPattern = "The double value at <{0}> must not be negative but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

    /**
     * Same as {@link #getBytes(String)} but with the guarantee that the returned long is greater zero.
     *
     * @param withConfigPath provides the config path to get the bytes value for.
     * @return the bytes value.
     * @throws DittoConfigError if the bytes value at the config path is negative.
     */
    default long getNonNegativeBytesOrThrow(final WithConfigPath withConfigPath) {
        final var result = getBytes(withConfigPath.getConfigPath());
        if (0L > result) {
            final var msgPattern = "The bytes value at <{0}> must not be negative but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, withConfigPath.getConfigPath(), result));
        }
        return result;
    }

}
