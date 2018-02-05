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
package org.eclipse.ditto.services.gateway.health;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.status.StatusHealthSupplier;

/**
 * Creates a composite from multiple {@link StatusHealthSupplier}s. This allows aggregation of health statuses.
 */
public class CompositeStatusHealthSupplier implements StatusHealthSupplier {

    private List<StatusHealthSupplier> suppliers;

    private CompositeStatusHealthSupplier(final List<StatusHealthSupplier> suppliers) {
        this.suppliers = Collections.unmodifiableList(new ArrayList<>(suppliers));
    }

    /**
     * Returns a new {@link CompositeStatusHealthSupplier}.
     *
     * @param suppliers the {@link StatusHealthSupplier}s which are used to provide a composite {@link StatusInfo}.
     * @return the {@link CompositeStatusHealthSupplier}.
     */
    public static StatusHealthSupplier of(final List<StatusHealthSupplier> suppliers) {
        requireNonNull(suppliers);

        return new CompositeStatusHealthSupplier(suppliers);
    }

    @Override
    public CompletionStage<StatusInfo> get() {
        final List<CompletableFuture<StatusInfo>> supplierFutures = suppliers.stream()
                .map(supplier -> supplier.get()
                .toCompletableFuture())
                .collect(Collectors.toList());

        return CompletableFutureUtils.collectAsList(supplierFutures)
                .thenApply(StatusInfo::composite);
    }

}
