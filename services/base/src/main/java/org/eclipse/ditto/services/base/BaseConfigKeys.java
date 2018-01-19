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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Adapts {@link BaseConfigKey}s to service-specific config keys. This is necessary as each service might have a custom
 * approach of naming configuration setting keys.
 */
@Immutable
public final class BaseConfigKeys {

    private final Map<BaseConfigKey, String> values;

    private BaseConfigKeys(final Map<BaseConfigKey, String> theConfigKeys) {
        values = Collections.unmodifiableMap(new HashMap<>(theConfigKeys));
    }

    /**
     * Returns a builder with a fluent API for an immutable {@code ConfigKeys}.
     *
     * @return the builder.
     */
    public static Builder getBuilder() {
        return new Builder();
    }

    /**
     * Checks if this config keys contains the specified base config key. This method is meant for pre-condition
     * checking.
     *
     * @param expectedBaseConfigKey the expected base config key.
     * @param furtherExpectedBaseConfigKeys further expected base config keys.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws java.lang.IllegalStateException if this config keys did not contain any of the expected base config
     * keys.
     */
    public void checkExistence(final BaseConfigKey expectedBaseConfigKey,
            final BaseConfigKey... furtherExpectedBaseConfigKeys) {

        checkNotNull(expectedBaseConfigKey, "expected base config key");
        checkNotNull(furtherExpectedBaseConfigKeys, "further expected base config keys");

        final Collection<BaseConfigKey> expectedConfigKeys = new ArrayList<>(1 + furtherExpectedBaseConfigKeys.length);
        expectedConfigKeys.add(expectedBaseConfigKey);
        Collections.addAll(expectedConfigKeys, furtherExpectedBaseConfigKeys);

        final Collection<BaseConfigKey> missingConfigKeys = expectedConfigKeys.stream()
                .filter(expectedConfigKey -> !values.containsKey(expectedConfigKey))
                .collect(Collectors.toList());

        if (!missingConfigKeys.isEmpty()) {
            final String msgPattern = "The base config keys did not contain <{0}>!";
            throw new IllegalStateException(MessageFormat.format(msgPattern, missingConfigKeys));
        }
    }

    /**
     * Returns the service-specific config key for the given base config key.
     *
     * @param baseConfigKey the base config key to get the associated service-specific config key for.
     * @return the service-specific config key or an empty Optional.
     * @throws NullPointerException if {@code baseConfigKey} is {@code null}.
     */
    public Optional<String> get(final BaseConfigKey baseConfigKey) {
        return Optional.ofNullable(values.get(checkBaseConfigKey(baseConfigKey)));
    }

    private static BaseConfigKey checkBaseConfigKey(final BaseConfigKey baseConfigKey) {
        return checkNotNull(baseConfigKey, "base config key");
    }

    /**
     * Returns the service-specific config key for the given base config key.
     * 
     * @param baseConfigKey the base config key to get the associated service-specific config key for.
     * @return the service-specific config key.
     * @throws NullPointerException if {@code baseConfigKey} is {@code null} or if {@code baseConfigKey} is not
     * associated with a service-specific config key.
     */
    public String getOrThrow(final BaseConfigKey baseConfigKey) {
        final String result = values.get(checkBaseConfigKey(baseConfigKey));
        if (null == result) {
            throw new NullPointerException(MessageFormat.format("Config key <{0}> is unknown!", baseConfigKey));
        }
        return result;
    }

    /**
     * A mutable builder with a fluent API for an immutable {@code ConfigKeys}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final Map<BaseConfigKey, String> configKeys;

        private Builder() {
            configKeys = new HashMap<>();
        }

        /**
         * Associates the given base config key with the given service-specific config key.
         *
         * @param baseConfigKey the base config key to be associated with {@code serviceSpecificConfigKey}.
         * @param serviceSpecificConfigKey the service-specific config key to be associated with
         * {@code baseConfigKey}.
         * @return this builder instance to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code serviceSpecificConfigKey} is empty.
         */
        public Builder put(final BaseConfigKey baseConfigKey, final CharSequence serviceSpecificConfigKey) {
            checkBaseConfigKey(baseConfigKey);
            argumentNotEmpty(serviceSpecificConfigKey, "service-specific config key");

            configKeys.put(baseConfigKey, serviceSpecificConfigKey.toString());

            return this;
        }

        private static BaseConfigKey checkBaseConfigKey(final BaseConfigKey baseConfigKey) {
            return checkNotNull(baseConfigKey, "base config key");
        }

        /**
         * Removes the association of the given base config key.
         *
         * @param baseConfigKey the base config key to be removed.
         * @return this builder instance to allow method chaining.
         * @throws NullPointerException if {@code baseConfigKey} is {@code null}.
         */
        public Builder remove(final BaseConfigKey baseConfigKey) {
            configKeys.remove(checkBaseConfigKey(baseConfigKey));
            return this;
        }

        /**
         * Creates a new instance of {@code ConfigKeys} containing the current values of this builder.
         *
         * @return the instance.
         */
        public BaseConfigKeys build() {
            return new BaseConfigKeys(configKeys);
        }

    }

}
