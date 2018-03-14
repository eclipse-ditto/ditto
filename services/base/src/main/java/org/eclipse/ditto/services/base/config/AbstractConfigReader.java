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
package org.eclipse.ditto.services.base.config;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.function.Function;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Superclass for accessors of {@link Config}.
 */
public abstract class AbstractConfigReader {

    private static final String PATH_DELIMITER = ".";

    /**
     * Underlying Config object.
     */
    protected final Config config;

    /**
     * Creates a AbstractConfigReader.
     *
     * @param config the underlying Config object.
     */
    protected AbstractConfigReader(final Config config) {
        this.config = config;
    }

    /**
     * Retrieve a value if the underlying config object has the required path.
     *
     * @param path config path to retrieve.
     * @param retriever function to retrieve value from the path. Typically of the form {@code config::getABC}.
     * @param <T> type of value to retrieve.
     * @return the retrieved value if the path exists in the underlying config, an empty optional otherwise.
     */
    protected <T> Optional<T> getIfPresent(final String path, final Function<String, T> retriever) {
        return config.hasPath(path)
                ? Optional.of(retriever.apply(path))
                : Optional.empty();
    }

    /**
     * Retrieve a child configuration by name.
     *
     * @param childPath path to the child.
     * @return the child configuration if it exists.
     *
     * @throws com.typesafe.config.ConfigException.Missing
     *             if value is absent or null
     * @throws com.typesafe.config.ConfigException.WrongType
     *             if value is not convertible to a Config
     */
    protected Config getChild(final String childPath) {
        return config.getConfig(childPath);
    }

    /**
     * Retrieve a child configuration by name.
     *
     * @param childPath path to the child.
     * @return the child configuration if it exists, an empty config otherwise.
     *
     * @throws com.typesafe.config.ConfigException.WrongType
     *             if value is not convertible to a Config
     */
    protected Config getChildOrEmpty(final String childPath) {
        return getIfPresent(childPath, config::getConfig).orElse(ConfigFactory.empty());
    }

    /**
     * Builds a config path from the given {@code pathElements}.
     * @param pathElements the path elements.
     * @return the config path.
     */
    protected static String path(final CharSequence... pathElements) {
        requireNonNull(pathElements);
        return String.join(PATH_DELIMITER, pathElements);
    }
}
