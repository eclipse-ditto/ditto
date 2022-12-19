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
package org.eclipse.ditto.base.service.config.limits;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the limits of Ditto services.
 */
@Immutable
public interface LimitsConfig {

    /**
     * Returns the maximum possible size of "Thing" entities in bytes.
     *
     * @return max size in bytes.
     */
    long getThingsMaxSize();

    /**
     * Returns the maximum possible size of "Policies" entities in bytes.
     *
     * @return max size in bytes.
     */
    long getPoliciesMaxSize();

    /**
     * Returns the maximum possible size of "Messages" entities in bytes.
     *
     * @return max size in bytes.
     */
    long getMessagesMaxSize();

    /**
     * Returns the default pagination size to apply when searching for "Things" via "things-search".
     *
     * @return default pagination size.
     */
    int getThingsSearchDefaultPageSize();

    /**
     * Retrieve the maximum pagination size to apply when searching for "Things" via "things-search".
     *
     * @return max pagination size.
     */
    int getThingsSearchMaxPageSize();

    /**
     * Return the number of allowed policy imports currently configured.
     *
     * @return the number of allowed policy imports.
     */
    int getPolicyImportsLimit();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code LimitsConfig}.
     */
    enum LimitsConfigValue implements KnownConfigValue {

        /**
         * The maximum possible size of "Thing" entities in bytes.
         */
        THINGS_MAX_SIZE("things.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

        /**
         * The maximum possible size of "Policies" entities in bytes.
         */
        POLICIES_MAX_SIZE("policies.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

        /**
         * The maximum possible size of "Messages" entities in bytes.
         */
        MESSAGES_MAX_SIZE("messages.max-size", Constants.DEFAULT_ENTITY_MAX_SIZE),

        /**
         * The default pagination size to apply when searching for "Things" via "things-search".
         */
        THINGS_SEARCH_DEFAULT_PAGE_SIZE(Constants.THINGS_SEARCH_PATH + "." + "default-page-size", 25),

        /**
         * The maximum pagination size to apply when searching for "Things" via "things-search".
         */
        THINGS_SEARCH_MAX_PAGE_SIZE(Constants.THINGS_SEARCH_PATH + "." + "max-page-size", 200),

        /**
         * The number of imports that a policy may contain.
         */
        POLICY_IMPORTS_LIMIT("imports-limit", 10);

        private final String path;
        private final Object defaultValue;

        private LimitsConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        /**
         * Constants to be used for the default values of the limits config.
         */
        @Immutable
        public static final class Constants {

            /**
             * The default maximum size of entities.
             */
            public static final long DEFAULT_ENTITY_MAX_SIZE = 100 * 1024L;

            /**
             * The config path expression common to to all config settings of the Things-Search service.
             */
            public static final String THINGS_SEARCH_PATH = "search";

            private Constants() {
                throw new AssertionError();
            }

        }

    }

}
