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

import {
  activeNamespaces,
  isValidNamespace,
  namespacesQueryParameter,
  parseNamespaces,
} from '../../modules/things/searchNamespaces';

describe('parseNamespaces', () => {
  test('splits, trims and drops blanks and duplicates', () => {
    expect(parseNamespaces(' org.eclipse.ditto, ,com.example,org.eclipse.ditto,'))
        .toEqual(['org.eclipse.ditto', 'com.example']);
  });

  test.each([undefined, null, '', ' , '])('returns no namespaces for %p', (value) => {
    expect(parseNamespaces(value)).toEqual([]);
  });
});

describe('activeNamespaces', () => {
  const available = ['org.eclipse.ditto', 'com.example', 'org.example'];

  test('uses all available namespaces if nothing was selected yet', () => {
    expect(activeNamespaces(available, undefined)).toEqual(available);
  });

  test('uses no namespace if everything was deselected', () => {
    expect(activeNamespaces(available, [])).toEqual([]);
  });

  test('keeps the order of the available namespaces and drops no longer available ones', () => {
    expect(activeNamespaces(available, ['org.example', 'removed', 'org.eclipse.ditto']))
        .toEqual(['org.eclipse.ditto', 'org.example']);
  });
});

describe('isValidNamespace', () => {
  test.each(['org', 'org.eclipse.ditto', 'com.example-ns', 'a_b.c1'])('accepts %p', (namespace) => {
    expect(isValidNamespace(namespace)).toBe(true);
  });

  test.each(['', '1org', 'org.', '.org', 'org..ditto', 'org.-ditto', 'org ditto', 'org:ditto', 'org,ditto'])(
      'rejects %p', (namespace) => {
        expect(isValidNamespace(namespace)).toBe(false);
      });
});

describe('namespacesQueryParameter', () => {
  test('joins the namespaces comma separated', () => {
    expect(namespacesQueryParameter(['org.eclipse.ditto', 'com.example']))
        .toBe('namespaces=org.eclipse.ditto,com.example');
  });

  test('is empty without namespaces', () => {
    expect(namespacesQueryParameter([])).toBe('');
  });
});
