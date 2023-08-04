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
package org.slf4j.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.MDCAdapter;

/**
 * This MDCAdapter delegates all of its methods to a {@link BasicMDCAdapter}.
 * The additional functionality of this class is to offer the possibility to observe the call of its methods.
 * This comes handy for unit testing as the MDC wouldn't be testable otherwise.
 *
 * @since 1.3.0
 */
@NotThreadSafe
public final class ObservableMdcAdapter implements MDCAdapter {

    private static final Map<String, MdcAdapterObserver> OBSERVERS = new HashMap<>(3);

    private final BasicMDCAdapter basicMdcAdapter;

    private ObservableMdcAdapter() {
        basicMdcAdapter = new BasicMDCAdapter();
    }

    public static ObservableMdcAdapter getInstance() {
        return new ObservableMdcAdapter();
    }

    public static void registerObserver(final String observerName, final MdcAdapterObserver observer) {
        OBSERVERS.put(observerName, observer);
    }

    public static void deregisterObserver(final String observerName) {
        OBSERVERS.remove(observerName);
    }

    public static void clearObservers() {
        OBSERVERS.clear();
    }

    @Override
    public void put(final String key, final String val) {
        notifyAllObservers(observer -> observer.onPut(key, val));
        basicMdcAdapter.put(key, val);
    }

    private static void notifyAllObservers(final Consumer<MdcAdapterObserver> observerConsumer) {
        OBSERVERS.values().forEach(observerConsumer);
    }

    @Override
    public String get(final String key) {
        notifyAllObservers(observer -> observer.onGet(key));
        return basicMdcAdapter.get(key);
    }

    @Override
    public void remove(final String key) {
        notifyAllObservers(observer -> observer.onRemove(key));
        basicMdcAdapter.remove(key);
    }

    @Override
    public void clear() {
        notifyAllObservers(MdcAdapterObserver::onClear);
        basicMdcAdapter.clear();
    }

    public Set<String> getKeys() {
        notifyAllObservers(MdcAdapterObserver::onGetKeys);
        return basicMdcAdapter.getKeys();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        notifyAllObservers(MdcAdapterObserver::onGetCopyOfContextMap);
        return basicMdcAdapter.getCopyOfContextMap();
    }

    @Override
    public void setContextMap(final Map<String, String> contextMap) {
        notifyAllObservers(observer -> observer.onSetContextMap(contextMap));
        basicMdcAdapter.setContextMap(contextMap);
    }

    /**
     * This call-back will be notified for each call of a {@link ObservableMdcAdapter}s method.
     */
    @NotThreadSafe
    public interface MdcAdapterObserver {

        void onPut(String key, String value);

        void onGet(String key);

        void onRemove(String key);

        void onClear();

        void onGetKeys();

        void onGetCopyOfContextMap();

        void onSetContextMap(Map<String, String> contextMap);

    }

    /**
     * Basic implementation of {@link MdcAdapterObserver}.
     * By default, the method of this class do nothing.
     * It can be sub-classed to implement only particular observer methods and not to have to implement all of them.
     */
    @NotThreadSafe
    public abstract static class AbstractMdcAdapterObserver implements MdcAdapterObserver {

        @Override
        public void onPut(final String key, final String value) {
            // Does nothing by default.
        }

        @Override
        public void onGet(final String key) {
            // Does nothing by default.
        }

        @Override
        public void onRemove(final String key) {
            // Does nothing by default.
        }

        @Override
        public void onClear() {
            // Does nothing by default.
        }

        @Override
        public void onGetKeys() {
            // Does nothing by default.
        }

        @Override
        public void onGetCopyOfContextMap() {
            // Does nothing by default.
        }

        @Override
        public void onSetContextMap(final Map<String, String> contextMap) {
            // Does nothing by default.
        }

    }

}
