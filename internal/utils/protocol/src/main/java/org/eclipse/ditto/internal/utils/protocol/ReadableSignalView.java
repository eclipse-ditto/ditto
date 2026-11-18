/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.protocol;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;

/**
 * The part of a signal one subscriber may read. Everything delivered to or evaluated on behalf of the subscriber
 * (payload, RQL filters, placeholders, extra fields, the {@code _context} of SSE) must be taken from this view.
 * Created by {@link PartialAccessSignalFilter#viewFor(Signal, AuthorizationContext)}.
 *
 * @since 3.9.9
 */
public final class ReadableSignalView {

    private final Signal<?> signal;
    @Nullable private final SubscriberAccess access;
    @Nullable private final Adaptable adaptable;

    private ReadableSignalView(final Signal<?> signal, @Nullable final Adaptable adaptable,
            @Nullable final SubscriberAccess access) {
        this.signal = signal;
        this.adaptable = adaptable;
        this.access = access;
    }

    static ReadableSignalView unrestricted(final Signal<?> signal, @Nullable final Adaptable adaptable) {
        return new ReadableSignalView(signal, adaptable, null);
    }

    static ReadableSignalView restricted(final Signal<?> readableSignal, final Adaptable readableAdaptable,
            final SubscriberAccess access) {
        return new ReadableSignalView(readableSignal, readableAdaptable, access);
    }

    /**
     * @return the readable signal; the original instance if the subscriber is not restricted. It does not carry the
     * extra fields.
     */
    public Signal<?> getSignal() {
        return signal;
    }

    /**
     * @return the readable signal as adaptable including its internal headers, without extra fields unless the
     * adaptable passed to {@link PartialAccessSignalFilter#viewFor(Signal, Adaptable, AuthorizationContext)} had some.
     * @throws IllegalStateException if the view is not {@link #isRestricted() restricted} and was created without an
     * adaptable, as then the caller's own adaptable of the signal is readable.
     */
    public Adaptable getAdaptable() {
        if (adaptable == null) {
            throw new IllegalStateException("The unrestricted view of <" + signal.getType() +
                    "> was created without an adaptable.");
        }
        return adaptable;
    }

    /**
     * @return whether the subscriber may read only a part of the signal, i.e. whether the readable signal or the
     * readable extra fields may differ from the original ones.
     */
    public boolean isRestricted() {
        return access != null;
    }

    /**
     * Removes what the subscriber must not read from thing-rooted JSON, e.g. the extra fields retrieved for the
     * signal or a thing built from the readable signal and the extra fields.
     *
     * @param thingJson the thing-rooted JSON.
     * @return the readable part of {@code thingJson}, the same instance if the subscriber is not restricted.
     */
    public JsonObject filterThingJson(final JsonObject thingJson) {
        return access == null ? thingJson : AdaptablePartialAccessFilter.filterThingJson(thingJson, access);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "signalType=" + signal.getType() +
                ", restricted=" + isRestricted() +
                "]";
    }

}
