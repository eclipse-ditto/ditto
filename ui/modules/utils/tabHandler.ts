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
import * as Environments from '../environments/environments.js';
import * as Authorization from '../environments/authorization.js';

/**
 * Common behaviour of all main tabs. Handles refresh of a tab after the environmant was changed
 * @param {Element} domTabItem 
 * @param {Element} domTabContent 
 * @param {function} onRefreshTab 
 * @param {string} envDisabledKey 
 * @returns 
 */

export function TabHandler(domTabItem, domTabContent, onRefreshTab, envDisabledKey = null) {
  const _domTabItem = domTabItem;
  const _domTabContent = domTabContent;
  const _refreshTab = onRefreshTab;
  const _envDisabledKey = envDisabledKey;
  let viewDirty = false;

  _domTabItem.addEventListener('click', onTabActivated);
  Environments.addChangeListener(onEnvironmentChanged);

  return {
    set viewDirty(newValue) {
      viewDirty = newValue;
    },
  };

  function onTabActivated() {
    Authorization.setForDevops(_domTabItem.dataset.auth === 'devOps');

    if (viewDirty) {
      _refreshTab.call(null, false);
      viewDirty = false;
    }
  }

  function onEnvironmentChanged(modifiedField) {
    _domTabItem.toggleAttribute('hidden', Environments.current()[_envDisabledKey] ?? false);
    if (!['pinnedThings', 'filterList', 'messageTemplates'].includes(modifiedField)) {
      if (_domTabContent.classList.contains('show')) {
        _refreshTab.call(null, true);
      } else {
        viewDirty = true;
      }
    }
  }
}


