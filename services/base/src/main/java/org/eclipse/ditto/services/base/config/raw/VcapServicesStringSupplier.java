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
package org.eclipse.ditto.services.base.config.raw;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoConfigError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class reads a config file of which the path is either provided as argument or is supposed to be set via
 * system environment variable {@value #VCAP_LOCATION_ENV_VARIABLE_NAME}.
 * For both alternatives dedicated static factory methods exist.
 * The content of the file is read as one String.
 * If the file is empty {@link #get()} returns an empty Optional.
 * If the variable denotes a non-existing file or a file that is not readable for some reason, {@link #get()} throws a
 * {@link org.eclipse.ditto.services.base.config.DittoConfigError} containing the cause and indicating the path of the
 * invalid config file.
 */
@Immutable
final class VcapServicesStringSupplier implements Supplier<Optional<String>> {

    /**
     * Name of the system environment variable for setting the path of the config file containing the VCAP settings.
     */
    static final String VCAP_LOCATION_ENV_VARIABLE_NAME = "VCAP_LOCATION";

    private static final Logger LOGGER = LoggerFactory.getLogger(VcapServicesStringSupplier.class);

    private final Path vcapServicesFilePath;

    private VcapServicesStringSupplier(final Path theVcapServicesFilePath) {
        vcapServicesFilePath = theVcapServicesFilePath;
    }

    /**
     * Returns an instance of {@code VcapConfigStringSupplier}.
     * The path of the VCAP services file is supposed to be set by system environment
     * variable {@value #VCAP_LOCATION_ENV_VARIABLE_NAME}.
     *
     * @return the instance.
     * @throws org.eclipse.ditto.services.base.config.DittoConfigError if the system environment variable
     * {@value #VCAP_LOCATION_ENV_VARIABLE_NAME} was either not set or is not a valid {@link java.nio.file.Path}.
     */
    static VcapServicesStringSupplier getInstance() {
        return of(tryToGetAsPath(System.getenv(VCAP_LOCATION_ENV_VARIABLE_NAME)));
    }

    private static Path tryToGetAsPath(final String vcapLocation) {
        try {
            return getAsPath(vcapLocation);
        } catch (final NullPointerException | InvalidPathException e) {
            throw new DittoConfigError(MessageFormat.format("<{0}> did not denote a valid path!", vcapLocation), e);
        }
    }

    private static Path getAsPath(final String vcapLocation) {
        if (null == vcapLocation) {
            final String msgPattern = "The system environment variable <{0}> was not set!";
            throw new NullPointerException(MessageFormat.format(msgPattern, VCAP_LOCATION_ENV_VARIABLE_NAME));
        }
        return Paths.get(vcapLocation);
    }

    /**
     * Returns an instance of {@code VcapServicesStringSupplier} based on the provided path.
     *
     * @param path the path of the VCAP services config file.
     * @return the instance.
     * @throws NullPointerException if {@code path} is {@code null}.
     */
    static VcapServicesStringSupplier of(final Path path) {
        return new VcapServicesStringSupplier(checkNotNull(path, "path of VCAP services (JSON) file"));
    }

    @Override
    public Optional<String> get() {
        return Optional.ofNullable(tryToReadConfigFile());
    }

    @Nullable
    private String tryToReadConfigFile() {
        try {
            return readConfigFile();
        } catch (final IOException e) {
            final String msgPattern = "Failed to read VCAP services config from path <{0}>!";
            final String errorMessage = MessageFormat.format(msgPattern, vcapServicesFilePath);
            LOGGER.error(errorMessage, e);
            throw new DittoConfigError(errorMessage, e);
        }
    }

    @Nullable
    private String readConfigFile() throws IOException {
        LOGGER.info("Reading VCAP services config from path <{}>.", vcapServicesFilePath);
        final String result = new String(Files.readAllBytes(vcapServicesFilePath));
        if (!result.isEmpty()) {
            return result;
        }
        return null;
    }

}
