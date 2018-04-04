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
package org.eclipse.ditto.services.utils.akka.functional;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

// TODO: javadoc
@FunctionalInterface
public interface Total<A, B> extends Function<A, B> {

    B apply(A x);

    default <D> Total<A, D> then(final Function<? super B, D> next) {
        return a -> next.apply(apply(a));
    }

    default <C> Total<C, B> after(final Function<C, ? extends A> prev) {
        return c -> apply(prev.apply(c));
    }

    default Partial<A, B> toPartial() {
        return then(Optional::of)::apply;
    }

    default <C> Total<C, CompletionStage<B>> afterAsync(final Function<C, CompletionStage<? extends A>> prev) {
        return c -> prev.apply(c).thenApply(this);
    }
}
