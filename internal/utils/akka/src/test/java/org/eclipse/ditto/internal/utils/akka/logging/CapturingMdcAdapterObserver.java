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
package org.eclipse.ditto.internal.utils.akka.logging;

import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.impl.ObservableMdcAdapter;

/**
 * This {@link org.slf4j.impl.ObservableMdcAdapter.MdcAdapterObserver} captures put entries and removed keys for later
 * test assertions.
 * <p>
 * since 1.3.0
 */
@ThreadSafe
final class CapturingMdcAdapterObserver extends ObservableMdcAdapter.AbstractMdcAdapterObserver {

    private final List<Map.Entry<String, String>> allPutEntries;
    private final List<String> allRemovedKeys;

    CapturingMdcAdapterObserver() {
        allPutEntries = Collections.synchronizedList(new ArrayList<>());
        allRemovedKeys = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public void onPut(final String key, final String value) {
        allPutEntries.add(entry(key, value));
    }

    @Override
    public void onRemove(final String key) {
        allRemovedKeys.add(key);
    }

    List<Map.Entry<?, ?>> getAllPutEntries() {
        return List.copyOf(allPutEntries);
    }

    List<String> getAllRemovedKeys() {
        return List.copyOf(allRemovedKeys);
    }

}
