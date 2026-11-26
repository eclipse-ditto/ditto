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
  mergeNamespaces,
  namespacesQueryParameter,
  parseNamespaces,
  parseValidNamespaces,
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

describe('parseValidNamespaces', () => {
  test('drops invalid namespaces', () => {
    expect(parseValidNamespaces('org.eclipse.ditto, 1invalid, org..ditto,com.example'))
        .toEqual(['org.eclipse.ditto', 'com.example']);
  });
});

describe('mergeNamespaces', () => {
  test('keeps the served namespaces and adds the ones only stored locally', () => {
    expect(mergeNamespaces('org.served, org.both', 'org.both,org.added')).toBe('org.served,org.both,org.added');
  });

  test.each([
    ['org.served', undefined, 'org.served'],
    [undefined, 'org.added', 'org.added'],
  ])('merges %p and %p to %p', (remote, local, expected) => {
    expect(mergeNamespaces(remote, local)).toBe(expected);
  });

  test('is undefined if neither is set', () => {
    expect(mergeNamespaces(undefined, undefined)).toBeUndefined();
  });
});

describe('activeNamespaces', () => {
  const available = ['org.eclipse.ditto', 'com.example', 'org.example'];

  test('uses all available namespaces if nothing was selected yet', () => {
    expect(activeNamespaces(available, undefined)).toEqual(available);
  });

  test('uses all namespaces if the selection is no longer available', () => {
    expect(activeNamespaces(available, ['removed'])).toEqual([]);
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
