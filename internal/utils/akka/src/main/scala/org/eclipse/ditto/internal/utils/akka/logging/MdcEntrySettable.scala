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
package org.eclipse.ditto.internal.utils.akka.logging

import javax.annotation.Nullable

/** This trait defines the means to put and remove entries to or from the MDC of a logger.
  *
  * @tparam L the type of the logger that implements this interface.
  * @since 1.4.0
  */
trait MdcEntrySettable[L] {

  /** Puts the specified diagnostic context value as identified by the specified key to this logger's MDC.
    * <p>
    * Providing `null` as value has the same effect as calling [[MdcEntrySettable removeMdcEntry(CharSequence)]] with
    * the specified key.
    * </p>
    *
    * @param key   the key which identifies the diagnostic value.
    * @param value the diagnostic value which is identified by `key`.
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException     if `key` is `null`.
    * @throws IllegalArgumentException if `key` is empty.
    */
  def withMdcEntry(key: CharSequence, @Nullable value: CharSequence): L

  /** Puts the specified diagnostic context values as identified by the specified keys to this logger's MDC.
    * <p>
    * Providing `null` for any value has the same effect as calling [[MdcEntrySettable removeMdcEntry(CharSequence)]]
    * with its associated key.
    * </p>
    *
    * @param k1 the first key which identifies the diagnostic value `v1`.
    * @param v1 the first diagnostic value which is identified by `k1`.
    * @param k2 the second key which identifies the diagnostic value `v2`.
    * @param v2 the second diagnostic value which is identified by `k2`.
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException     if `k1` or `k2` is `null`.
    * @throws IllegalArgumentException if `k1` or `k2` is empty.
    */
  def withMdcEntries(k1: CharSequence, @Nullable v1: CharSequence, k2: CharSequence, @Nullable v2: CharSequence): L

  /** Puts the specified diagnostic context values as identified by the specified keys to this logger's MDC.
    * <p>
    * Providing `null` for any value has the same effect as calling [[MdcEntrySettable removeMdcEntry(CharSequence)}]]
    * with its associated key.
    * </p>
    *
    * @param k1 the first key which identifies the diagnostic value `v1`.
    * @param v1 the first diagnostic value which is identified by `k1`.
    * @param k2 the second key which identifies the diagnostic value `v2`.
    * @param v2 the second diagnostic value which is identified by `k2`.
    * @param k3 the third key which identifies the diagnostic value `v3`.
    * @param v3 the third diagnostic value which is identified by `k3`.
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException     if `k1`, `k2` or `k3` is `null`.
    * @throws IllegalArgumentException if `k1`, `k2` or `k3` is empty.
    */
  def withMdcEntries(k1: CharSequence, @Nullable v1: CharSequence,
                     k2: CharSequence, @Nullable v2: CharSequence,
                     k3: CharSequence, @Nullable v3: CharSequence): L

  /** Puts the given entry (entries) to the MDC of this logger.
    *
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException if any argument is `null`.
    */
  @annotation.varargs def withMdcEntry(mdcEntry: MdcEntry, furtherMdcEntries: MdcEntry*): L

  /** Removes the diagnostic context value identified by the specified key.
    * This method does nothing if there is no previous value associated with the specified key.
    *
    * @param key the key of the value to be removed.
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException     if `key` is `null`.
    * @throws IllegalArgumentException if `key` is empty.
    */
  def removeMdcEntry(key: CharSequence): L

}
