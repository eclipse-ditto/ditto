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

/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';

let dom = {
  buttonLoadAllLogLevels: null,
  divLoggers: null,
  templateLogger: null,
  tabOperations: null,
  collapseOperations: null,
};

export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);
  Utils.getAllElementsById(dom);

  dom.tabOperations.onclick = onTabActivated;
  dom.buttonLoadAllLogLevels.onclick = loadAllLogLevels;
}

function onUpdateLoggingClick(service, logConfig) {
  API.callDittoREST('PUT', '/devops/logging/' + service, logConfig, null, false, true)
      .then(() => {
        loadAllLogLevels();
      });
}

function loadAllLogLevels() {
  API.callDittoREST('GET', '/devops/logging', null, null, false, true)
      .then((result) => createLoggerView(result))
      .catch((error) => {
      });
}

function createLoggerView(allLogLevels) {
  dom.divLoggers.innerHTML = '';
  Object.keys(allLogLevels).sort().forEach((service, i) => {
    let heading = document.createElement('h6');
    heading.innerText = service;
    if (i > 0) {
      heading.classList.add('mt-3');
    }
    dom.divLoggers.append(heading);
    Object.values(allLogLevels[service])[0].loggerConfigs.forEach((logConfig) => {
      let row = document.createElement('div');
      row.attachShadow({mode: 'open'});
      row.shadowRoot.append(dom.templateLogger.content.cloneNode(true));
      row.shadowRoot.getElementById('inputLogger').value = logConfig.logger;
      row.shadowRoot.getElementById(logConfig.level).setAttribute('checked', '');
      Array.from(row.shadowRoot.querySelectorAll('.btn-check')).forEach((btn) => {
        btn.service = service;
        btn.logger = logConfig.logger;
        btn.addEventListener('click', (event) => onUpdateLoggingClick(event.target.service, {
          logger: event.target.logger,
          level: event.target.id,
        }));
      });
      dom.divLoggers.append(row);
    });
  });
}

let viewDirty = false;

function onTabActivated() {
  if (viewDirty) {
    loadAllLogLevels();
    viewDirty = false;
  }
}

function onEnvironmentChanged() {
  if (dom.collapseOperations.classList.contains('show')) {
    loadAllLogLevels();
  } else {
    viewDirty = true;
  }
}
