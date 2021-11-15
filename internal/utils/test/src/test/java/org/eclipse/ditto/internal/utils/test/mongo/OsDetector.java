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

 final class OsDetector {

     private OsDetector() {
         // No-Op because this is a utility class.
     }

     private static final String OS = System.getProperty("os.name").toLowerCase();

     static boolean isWindows() {
         return OS.contains("win");
     }

     static boolean isMac() {
         return OS.contains("mac");
     }

 }
