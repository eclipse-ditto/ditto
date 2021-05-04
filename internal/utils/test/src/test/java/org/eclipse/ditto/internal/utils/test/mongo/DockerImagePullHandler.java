/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.test.mongo;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;

/**
 * Allows to watch completion of image pulling process and logs its progress.
 */
final class DockerImagePullHandler implements ResultCallback<PullResponseItem> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerImagePullHandler.class);

    @Nullable
    private Closeable closeable;
    private final CompletableFuture<Void> imagePullFuture;

    private DockerImagePullHandler() {
        imagePullFuture = new CompletableFuture<>();
    }

    static DockerImagePullHandler newInstance() {
        return new DockerImagePullHandler();
    }

    @Override
    public void onStart(final Closeable closeable) {
        LOGGER.info("Pulling docker image started. Closable: <{}>.", closeable);
        this.closeable = closeable;
    }

    @Override
    public void onNext(final PullResponseItem pullResponseItem) {
        LOGGER.info("Got next pull response item <{}>.", pullResponseItem);
    }

    @Override
    public void onError(final Throwable throwable) {
        LOGGER.error("Got error during pulling image.", throwable);
        imagePullFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        LOGGER.info("Pulling docker image completed.");
        imagePullFuture.complete(null);
    }

    public CompletableFuture<Void> getImagePullFuture() {
        return imagePullFuture;
    }

    @Override
    public void close() throws IOException {
        if (closeable != null) {
            LOGGER.info("Aborting pulling docker image.");
            closeable.close();
            imagePullFuture.complete(null);
        }
    }
}
