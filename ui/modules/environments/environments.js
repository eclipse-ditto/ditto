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

/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as Authorization from '../environments/authorization.js';
import * as Utils from '../utils.js';

let environments = {
  local_ditto: {
    api_uri: 'http://localhost:8080',
    ditto_version: '3',
    bearer: null,
    defaultUsernamePassword: 'ditto:ditto',
    defaultUsernamePasswordDevOps: 'devops:foobar',
    useBasicAuth: true,
    useDittoPreAuthenticatedAuth: false,
    defaultDittoPreAuthenticatedUsername: null,
  },
  local_ditto_ide: {
    api_uri: 'http://localhost:8080',
    ditto_version: '3',
    bearer: null,
    defaultUsernamePassword: null,
    defaultUsernamePasswordDevOps: null,
    useBasicAuth: false,
    useDittoPreAuthenticatedAuth: true,
    defaultDittoPreAuthenticatedUsername: 'pre:ditto',
  },
  ditto_sandbox: {
    api_uri: 'https://ditto.eclipseprojects.io',
    ditto_version: '3',
    bearer: null,
    defaultUsernamePassword: 'ditto:ditto',
    defaultUsernamePasswordDevOps: null,
    useBasicAuth: true,
    useDittoPreAuthenticatedAuth: false,
    defaultDittoPreAuthenticatedUsername: null,
  },
};

let settingsEditor;

let dom = {
  environmentSelector: null,
  buttonCreateEnvironment: null,
  buttonDeleteEnvironment: null,
  buttonUpdateFields: null,
  buttonUpdateJson: null,
  inputEnvironmentName: null,
  inputApiUri: null,
  inputDittoVersion: null,
  tbodyEnvironments: null,
};

let observers = [];

export function current() {
  return environments[dom.environmentSelector.value];
}

export function addChangeListener(observer) {
  observers.push(observer);
}

function notifyAll(modifiedField) {
  // Notify Authorization first to set right auth header
  Authorization.onEnvironmentChanged(modifiedField);
  // Notify others
  observers.forEach(observer => observer.call(null, modifiedField));
}

export function ready() {
  Utils.getAllElementsById(dom);

  const restoredEnv = localStorage.getItem('ditto-ui-env');
  if (restoredEnv) {
    environments = JSON.parse(restoredEnv);
  }

  Object.keys(environments).forEach((env) => {
    Object.defineProperties(environments[env], {
      bearer: {
        enumerable: false,
        writable: true,
      },
      usernamePassword: {
        value: environments[env].defaultUsernamePassword,
        enumerable: false,
        writable: true,
      },
      usernamePasswordDevOps: {
        value: environments[env].defaultUsernamePasswordDevOps,
        enumerable: false,
        writable: true,
      },
      dittoPreAuthenticatedUsername: {
        value: environments[env].defaultDittoPreAuthenticatedUsername,
        enumerable: false,
        writable: true,
      },
    });
  });

  settingsEditor = ace.edit('settingsEditor');
  settingsEditor.session.setMode('ace/mode/json');

  dom.buttonUpdateJson.onclick = () => {
    environments[dom.inputEnvironmentName.value] = JSON.parse(settingsEditor.getValue());
    environmentsJsonChanged();
  };

  dom.buttonUpdateFields.onclick = () => {
    environments[dom.inputEnvironmentName.value].api_uri = dom.inputApiUri.value;
    environments[dom.inputEnvironmentName.value].ditto_version = dom.inputDittoVersion.value;
    environmentsJsonChanged();
  };

  dom.tbodyEnvironments.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      dom.inputEnvironmentName.value = event.target.parentNode.id;
      updateEnvEditors();
    }
  });

  dom.buttonCreateEnvironment.onclick = () => {
    Utils.assert(dom.inputEnvironmentName.value, 'Please provide an environment name', dom.inputEnvironmentName);
    Utils.assert(!environments[dom.inputEnvironmentName.value], 'Name already used', dom.inputEnvironmentName);
    environments[dom.inputEnvironmentName.value] = {
      api_uri: dom.inputApiUri.value ? dom.inputApiUri.value : '',
      ditto_version: dom.ditto_version.value ? dom.inputDittoVersion.value : '3',
    };
    environmentsJsonChanged();
  };

  dom.buttonDeleteEnvironment.onclick = () => {
    Utils.assert(dom.inputEnvironmentName.value, 'No environment selected');
    Utils.assert(Object.keys(environments).length >= 2, 'At least one environment is required');
    delete environments[dom.inputEnvironmentName.value];
    dom.inputEnvironmentName.value = null;
    environmentsJsonChanged();
  };

  // Ensure that ace editor to refresh when updated while hidden
  document.querySelector('a[data-bs-target="#tabEnvJson"]').addEventListener('shown.bs.tab', () => {
    settingsEditor.renderer.updateFull();
  });

  document.getElementById('environmentSelector').onchange = () => {
    notifyAll();
  };

  environmentsJsonChanged();
}

export function environmentsJsonChanged(modifiedField) {
  localStorage.setItem('ditto-ui-env', JSON.stringify(environments));

  updateEnvSelector();
  updateEnvEditors();
  updateEnvTable();

  notifyAll(modifiedField);

  function updateEnvSelector() {
    let activeEnvironment = dom.environmentSelector.value;
    if (!activeEnvironment || !environments[activeEnvironment]) {
      activeEnvironment = Object.keys(environments)[0];
    }

    Utils.setOptions(dom.environmentSelector, Object.keys(environments));

    dom.environmentSelector.value = activeEnvironment;
  }

  function updateEnvTable() {
    dom.tbodyEnvironments.innerHTML = '';
    Object.keys(environments).forEach((key) => {
      Utils.addTableRow(dom.tbodyEnvironments, key, key === dom.inputEnvironmentName.value);
    });
  }
}

function updateEnvEditors() {
  const selectedEnvironment = environments[dom.inputEnvironmentName.value];
  if (selectedEnvironment) {
    settingsEditor.setValue(JSON.stringify(selectedEnvironment, null, 2), -1);
    dom.inputApiUri.value = selectedEnvironment.api_uri;
    dom.inputDittoVersion.value = selectedEnvironment.ditto_version ? selectedEnvironment.ditto_version : '3';
  } else {
    dom.inputEnvironmentName.value = null;
    settingsEditor.setValue('');
    dom.inputApiUri.value = null;
    dom.inputDittoVersion.value = null;
  }
}

