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
import * as Authorization from './authorization.js';
import * as Utils from '../utils.js';
import defaultTemplates from './environmentTemplates.json';
import environmentsHTML from './environments.html';


const URL_PRIMARY_ENVIRONMENT_NAME = 'primaryEnvironmentName';
const URL_ENVIRONMENTS = 'environmentsURL';
const STORAGE_KEY = 'ditto-ui-env';

let urlSearchParams;
let environments;
let selectedEnvName;

let settingsEditor;

let dom = {
  environmentSelector: null,
  tbodyEnvironments: null,
  tableValidationEnvironments: null,
  crudEnvironmentFields: null,
  crudEnvironmentJson: null,
  inputApiUri: null,
  inputSearchNamespaces: null,
  selectDittoVersion: null,
  inputTabPolicies: null,
  inputTabConnections: null,
  inputTabOperations: null,
};

let observers = [];

document.getElementById('environmentsHTML').innerHTML = environmentsHTML;

function Environment(env) {
  Object.assign(this, env);
  Object.defineProperties(this, {
    bearer: {
      enumerable: false,
      writable: true,
    },
    bearerDevOps: {
      enumerable: false,
      writable: true,
    },
    usernamePassword: {
      value: this.defaultUsernamePassword,
      enumerable: false,
      writable: true,
    },
    usernamePasswordDevOps: {
      value: this.defaultUsernamePasswordDevOps,
      enumerable: false,
      writable: true,
    },
    dittoPreAuthenticatedUsername: {
      value: this.defaultDittoPreAuthenticatedUsername,
      enumerable: false,
      writable: true,
    },
  });
}

export function current() {
  return environments[dom.environmentSelector.value];
}

export function addChangeListener(observer) {
  observers.push(observer);
}

function notifyAll(modifiedField = null) {
  // Notify Authorization first to set right auth header
  Authorization.onEnvironmentChanged();
  // Notify others
  observers.forEach(observer => observer.call(null, modifiedField));
}

export async function ready() {
  Utils.getAllElementsById(dom);

  Utils.addValidatorToTable(dom.tbodyEnvironments, dom.tableValidationEnvironments);

  urlSearchParams = new URLSearchParams(window.location.search);
  environments = await loadEnvironmentTemplates();

  settingsEditor = Utils.createAceEditor('settingsEditor', 'ace/mode/json', true);

  dom.tbodyEnvironments.addEventListener('click', onEnvironmentsTableClick);

  dom.crudEnvironmentJson.addEventListener('onCreateClick', onCreateEnvironmentClick);
  dom.crudEnvironmentJson.addEventListener('onUpdateClick', onUpdateEnvironmentClick);
  dom.crudEnvironmentJson.addEventListener('onDeleteClick', onDeleteEnvironmentClick);
  dom.crudEnvironmentJson.addEventListener('onEditToggle', onEditToggle);

  dom.crudEnvironmentFields.addEventListener('onCreateClick', onCreateEnvironmentClick);
  dom.crudEnvironmentFields.addEventListener('onUpdateClick', onUpdateEnvironmentClick);
  dom.crudEnvironmentFields.addEventListener('onDeleteClick', onDeleteEnvironmentClick);
  dom.crudEnvironmentFields.addEventListener('onEditToggle', onEditToggle);

  dom.environmentSelector.onchange = onEnvironmentSelectorChange;

  // Ensure that ace editor to refresh when updated while hidden
  document.querySelector('a[data-bs-target="#tabEnvJson"]').addEventListener('shown.bs.tab', () => {
    settingsEditor.renderer.updateFull();
  });


  environmentsJsonChanged();
}

function onEnvironmentSelectorChange() {
  urlSearchParams.set(URL_PRIMARY_ENVIRONMENT_NAME, dom.environmentSelector.value);
  window.history.replaceState({}, '', `${window.location.pathname}?${urlSearchParams}`);
  notifyAll();
}

function onDeleteEnvironmentClick() {
  Utils.assert(selectedEnvName, 'No environment selected', dom.tableValidationEnvironments);
  Utils.assert(Object.keys(environments).length >= 2, 'At least one environment is required',
      dom.tableValidationEnvironments);
  delete environments[selectedEnvName];
  selectedEnvName = null;
  environmentsJsonChanged();
}

function onCreateEnvironmentClick(event) {
  Utils.assert(event.target.idValue,
      'Environment name must not be empty',
      event.target.validationElement);
  Utils.assert(!environments[event.target.idValue],
      'Environment name already used',
      event.target.validationElement);

  if (event.target === dom.crudEnvironmentFields) {
    environments[event.target.idValue] = new Environment({
      api_uri: dom.inputApiUri.value ?? '',
      searchNamespaces: dom.inputSearchNamespaces.value ?? '',
      ditto_version: dom.selectDittoVersion.value ? dom.selectDittoVersion.value : '3',
    });
  } else {
    environments[event.target.idValue] = new Environment(JSON.parse(settingsEditor.getValue()));
  }

  selectedEnvName = event.target.idValue;
  event.target.toggleEdit();
  environmentsJsonChanged();
}

function onEnvironmentsTableClick(event) {
  if (event.target && event.target.tagName === 'TD') {
    if (selectedEnvName && selectedEnvName === event.target.parentNode.id) {
      selectedEnvName = null;
    } else {
      selectedEnvName = event.target.parentNode.id;
    }
    updateEnvEditors();
  }
}

function onUpdateEnvironmentClick(event) {
  if (selectedEnvName !== event.target.idValue) {
    changeEnvironmentName();
  }
  if (event.target === dom.crudEnvironmentFields) {
    environments[selectedEnvName].api_uri = dom.inputApiUri.value;
    environments[selectedEnvName].searchNamespaces = dom.inputSearchNamespaces.value;
    environments[selectedEnvName].ditto_version = dom.selectDittoVersion.value;
    environments[selectedEnvName].disablePolicies = !dom.inputTabPolicies.checked;
    environments[selectedEnvName].disableConnections = !dom.inputTabConnections.checked;
    environments[selectedEnvName].disableOperations = !dom.inputTabOperations.checked;
  } else {
    environments[selectedEnvName] = JSON.parse(settingsEditor.getValue());
  }
  event.target.toggleEdit();
  environmentsJsonChanged();

  function changeEnvironmentName() {
    environments[event.target.idValue] = environments[selectedEnvName];
    delete environments[selectedEnvName];
    selectedEnvName = event.target.idValue;
  }
}

export function environmentsJsonChanged(modifiedField = null) {
  environments && localStorage.setItem(STORAGE_KEY, JSON.stringify(environments));

  updateEnvSelector();
  updateEnvEditors();
  updateEnvTable();

  notifyAll(modifiedField);

  function updateEnvSelector() {
    let activeEnvironment = dom.environmentSelector.value;
    if (!activeEnvironment) {
      activeEnvironment = urlSearchParams.get(URL_PRIMARY_ENVIRONMENT_NAME);
    }
    if (!activeEnvironment || !environments[activeEnvironment]) {
      activeEnvironment = Object.keys(environments)[0];
    }

    Utils.setOptions(dom.environmentSelector, Object.keys(environments));

    dom.environmentSelector.value = activeEnvironment;
  }

  function updateEnvTable() {
    dom.tbodyEnvironments.innerHTML = '';
    Object.keys(environments).forEach((key) => {
      Utils.addTableRow(dom.tbodyEnvironments, key, key === selectedEnvName);
    });
  }
}

function updateEnvEditors() {
  if (selectedEnvName) {
    const selectedEnvironment = environments[selectedEnvName];
    dom.crudEnvironmentFields.idValue = selectedEnvName;
    dom.crudEnvironmentJson.idValue = selectedEnvName;
    settingsEditor.setValue(Utils.stringifyPretty(selectedEnvironment), -1);
    dom.inputApiUri.value = selectedEnvironment.api_uri;
    dom.inputSearchNamespaces.value = selectedEnvironment.searchNamespaces ?? '';
    dom.selectDittoVersion.value = selectedEnvironment.ditto_version ? selectedEnvironment.ditto_version : '3';
    dom.inputTabPolicies.checked = !selectedEnvironment.disablePolicies;
    dom.inputTabConnections.checked = !selectedEnvironment.disableConnections;
    dom.inputTabOperations.checked = !selectedEnvironment.disableOperations;
  } else {
    dom.crudEnvironmentFields.idValue = null;
    dom.crudEnvironmentJson.idValue = null;
    settingsEditor.setValue('');
    dom.inputApiUri.value = null;
    dom.inputSearchNamespaces.value = null;
    dom.selectDittoVersion.value = 3;
    dom.inputTabPolicies.checked = true;
    dom.inputTabConnections.checked = true;
    dom.inputTabOperations.checked = true;
  }
}

async function loadEnvironmentTemplates() {
  let fromURL;
  let fromLocalStorage;

  let environmentsURL = urlSearchParams.get(URL_ENVIRONMENTS);
  if (environmentsURL) {
    try {
      let response = await fetch(environmentsURL);
      if (!response.ok) {
        throw new Error(`URL ${environmentsURL} can not be loaded`);
      }
      fromURL = await response.json();
      validateEnvironments(fromURL);
    } catch (err) {
      fromURL = null;
      Utils.showError(
          err.message,
          'Error loading environments from URL');
    }
  }

  const restoredEnv = localStorage.getItem(STORAGE_KEY);
  if (restoredEnv) {
    fromLocalStorage = JSON.parse(restoredEnv);
  }

  let result;

  if (fromURL) {
    if (fromLocalStorage) {
      result = merge(fromURL, fromLocalStorage);
    } else {
      result = fromURL;
    }
  } else if (fromLocalStorage) {
    result = fromLocalStorage;
  } else {
    result = defaultTemplates;
  }

  Object.keys(result).forEach((env) => {
    result[env] = new Environment(result[env]);
  });

  return result;

  function validateEnvironments(envs) {
    Object.keys(envs).forEach((key) => {
      if (!envs[key].hasOwnProperty('api_uri')) {
        throw new SyntaxError('Environments json invalid');
      }
    });
  };

  function merge(remote, local) {
    let merged = {};
    Object.keys(remote).forEach((env) => {
      merged[env] = {
        ...local[env],
        ...remote[env],
      };
    });
    return {
      ...local,
      ...merged,
    };
  }
}

function onEditToggle(event) {
  dom.inputApiUri.disabled = !event.detail.isEditing;
  dom.inputSearchNamespaces.disabled = !event.detail.isEditing;
  dom.selectDittoVersion.disabled = !event.detail.isEditing;
  dom.inputTabPolicies.disabled = !event.detail.isEditing;
  dom.inputTabConnections.disabled = !event.detail.isEditing;
  dom.inputTabOperations.disabled = !event.detail.isEditing;
  settingsEditor.setReadOnly(!event.detail.isEditing);
  settingsEditor.renderer.setShowGutter(event.detail.isEditing);
  if (!event.detail.isEditing) {
    updateEnvEditors();
  }
}
