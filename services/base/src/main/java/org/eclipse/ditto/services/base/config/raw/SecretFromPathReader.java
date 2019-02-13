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
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The purpose of this class is to create a {@link Secret} based on a file which is denoted by a
 * {@link java.nio.file.Path}.
 * The result of the {@link #get()} method is an Optional.
 * If the denoted file is empty the result Optional is empty, too.
 * Otherwise, a {@code Secret} object is created as follows: The file name is the name of the result Secret and the
 * file content is the value of the result Secret.
 */
@Immutable
final class SecretFromPathReader implements Supplier<Optional<Secret>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretFromPathReader.class);

    private final Path path;

    private SecretFromPathReader(final Path thePath) {
        path = thePath;
    }

    /**
     * Returns an instance of {@code SecretFromPathReader}.
     *
     * @param path denotes a file that is supposed to contain the information for creating a {@link Secret} object.
     * @return the instance.
     * @throws NullPointerException if {@code path} is {@code null}.
     */
    static SecretFromPathReader of(final Path path) {
        return new SecretFromPathReader(checkNotNull(path, "Secret path"));
    }

    @Override
    public Optional<Secret> get() {
        return Optional.ofNullable(tryToReadSecretFromPath());
    }

    @Nullable
    private Secret tryToReadSecretFromPath() {
        try {
            return readSecretFromPath();
        } catch (final IOException | IllegalStateException e) {
            LOGGER.warn("Failed to read secret at path <{}>!", path);
            return null;
        }
    }

    private Secret readSecretFromPath() throws IOException {
        final String secretName = String.valueOf(path.getName(path.getNameCount() - 1));
        final List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            final String msgTemplate = "Expected a secret but file <{0}> was empty!";
            throw new IllegalStateException(MessageFormat.format(msgTemplate, path));
        }
        return Secret.newInstance(secretName, lines.get(0));
    }

}
