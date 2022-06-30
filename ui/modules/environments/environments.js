/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import * as API from '../api.js';
/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */
import * as Utils from '../utils.js';

let environments = {
  local_ditto: {
    api_uri: 'http://localhost:8080',
    solutionId: null,
    bearer: null,
    usernamePassword: 'ditto:ditto',
    usernamePasswordDevOps: 'devops:foobar',
    useBasicAuth: true,
  },
};

let theEnv;
let settingsEditor;

let dom = {
  environmentSelector: null,
};

let observers = [];

export function current() {
  return environments[theEnv];
};

export function addChangeListener(observer) {
  observers.push(observer);
}

function notifyAll() {
  observers.forEach(observer => observer.call());
}

export function ready() {
  Utils.getAllElementsById(dom);

  const restoredEnv = localStorage.getItem('ditto-ui-env');
  if (restoredEnv) {
    environments = JSON.parse(restoredEnv);
  }

  settingsEditor = ace.edit('settingsEditor');
  settingsEditor.session.setMode('ace/mode/json');
  settingsEditor.setValue(JSON.stringify(environments, null, 2), -1);
  environmentsJsonChanged();

  settingsEditor.on('blur', () => {
    environments = JSON.parse(settingsEditor.getValue());
    environmentsJsonChanged();
  });

  document.getElementById('tabEnvironments').onclick = () => {
    settingsEditor.setValue(JSON.stringify(environments, null, 2), -1);
  };

  document.querySelectorAll('.mainUser,.devOpsUser').forEach((menuTab) => {
    menuTab.addEventListener('click', (event) => {
      API.setAuthHeader(event.target.parentNode.classList.contains('devOpsUser'));
    });
  });

  document.getElementById('environmentSelector').onchange = (event) => {
    theEnv = event.target.value;
    activateEnvironment();
  };
}

export function togglePinnedThing(evt) {
  if (evt.target.checked) {
    current().pinnedThings.push(this.id);
  } else {
    const index = current().pinnedThings.indexOf(this.id);
    if (index > -1) {
      current().pinnedThings.splice(index, 1);
    };
  };
  environmentsJsonChanged();
};

export function environmentsJsonChanged() {
  localStorage.setItem('ditto-ui-env', JSON.stringify(environments));

  dom.environmentSelector.innerHTML = '';
  if (theEnv && !current()) {
    theEnv = null;
  };
  for (const key of Object.keys(environments)) {
    let option = document.createElement('option');
    option.text = key;
    dom.environmentSelector.add(option);
    if (!theEnv) {
      theEnv = key;
    };
  };
  dom.environmentSelector.value = theEnv;
  activateEnvironment();
}

function activateEnvironment() {
  if (!current()['pinnedThings']) {
    current().pinnedThings = [];
  };

  notifyAll();
}
