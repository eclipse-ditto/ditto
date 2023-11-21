/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
/* eslint-disable require-jsdoc */


export function Observable() {
  
  let observers: Array<Function> = [];

  return {
    addChangeListener,
    notifyAll,
  };

  function addChangeListener(observer: Function) {
    observers.push(observer);
  }

  function notifyAll(context: Object) {
    observers.forEach((observer) => observer.call(null, context));
  }
}


