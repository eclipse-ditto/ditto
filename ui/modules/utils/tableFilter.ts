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

import { JSONPath } from 'jsonpath-plus';
import * as Utils from '../utils.js';
import tableFilterHTML from './tableFilter.html';

interface Filters {
  searchLike: Array<string>,
  searchPropertiesEq: Object
};

enum Mode {
  BASIC=0,
  ADVANCED=1,
}

export class TableFilter extends HTMLElement {


  basicFilters = {};
  mode = Mode.BASIC;
  mainInput: HTMLInputElement;
  validatedFilter: string;
  constructor() {
    super();
  }
  connectedCallback() {
    this.innerHTML = tableFilterHTML;
  
    this.mainInput = this.querySelector('input[name="main"]');
    this.mainInput.addEventListener('keyup', this.mainInputChangedCallback(this));

    this.querySelector('ul').addEventListener('click', this.filterSelectedCallback(this));

    this.querySelector('input[role="switch"').addEventListener('click', this.toggleModeCallback(this));

    this.querySelector('input[name="dropdown"]').addEventListener('keyup', this.filterDropdown(this));
    this.querySelector('input[name="dropdown"]').addEventListener('search', this.filterDropdown(this));
  }

  set filterOptions(value: [string?]) {
    const el = this.querySelector('ul') as HTMLUListElement;
    value.forEach((entry) => {
      Utils.addDropDownEntry(el, entry, false);
    })
  }

  get filter() {
    return this.validatedFilter;
  }

  filterItems(items: Array<any>) {
    let result = items;
    if (this.validatedFilter) {
      try {
        result = JSONPath({
          path: this.validatedFilter,
          json: items,
        });
      }
      catch (error) {
        console.log(error.message);
      }
      
    }
    return result;
  }
  
      
  private addFilter(key: string, value: string) {
    if (!this.basicFilters[key]) {
      this.basicFilters[key] = [];
    }  
    this.basicFilters[key] = [...new Set([
      ...value.split(','),
      ...this.basicFilters[key]
    ])];  
  }  

  private createJsonPath(filter) {
    if (!Object.keys(filter).length) {
      return null;
    }
    
    // $[?(@.x==2&&(@.type=="home"||@.type=="work"))]
    let ors = [];
    Object.keys(filter).forEach((key) => {
      ors.push(filter[key].map((value) => `@.${key}=="${value}"`).join('||'));
    })
    const ands = ors.map((part) => `(${part})`).join('&&');
    return `$[?(${ands})]`;
  }

  private fillUIChips() {
    const div = this.querySelector('[data-bs-theme="dark"]') as HTMLElement;
    div.innerHTML = '';
    Object.keys(this.basicFilters).forEach((key) => {
      const chip = document.createElement('span');
      chip.classList.add('badge', 'rounded-pill', 'bg-info', 'mb-1', 'me-1');
      chip.innerText = `${key}:${this.basicFilters[key]}`;
      const button = document.createElement('button');
      button.type = 'button';
      button.classList.add('btn-close');
      button.style.fontSize = '0.75em';
      button.setAttribute('data-key', key);
      button.onclick = this.deleteFilterCallback(this);
      chip.appendChild(button);
      div.appendChild(chip);
    })
  }
  
  private deleteFilterCallback(tableFilter: TableFilter) {
    return (event: PointerEvent) => {
      const key = (event.target as HTMLButtonElement).getAttribute('data-key');
      delete tableFilter.basicFilters[key];
      this.filterChanged()
    }
  }

  private toggleModeCallback(tableFilter: TableFilter) {
    return (event) => {
      switch(tableFilter.mode) {
        case Mode.BASIC:
          tableFilter.mainInput.value = tableFilter.filter;
          tableFilter.mainInput.placeholder = 'Enter JSONPath to filter...';
          (tableFilter.querySelector('[data-bs-theme="dark"]') as HTMLElement).hidden = true;
          tableFilter.mode = Mode.ADVANCED;
          break;
        case Mode.ADVANCED:
          tableFilter.mainInput.value = '';
          tableFilter.mainInput.placeholder = 'Add basic filter...';
          (tableFilter.querySelector('[data-bs-theme="dark"]') as HTMLElement).hidden = false;
          tableFilter.mode = Mode.BASIC;
          this.basicFilters = {};
          this.filterChanged();
      }
    }
  }

  private filterSelectedCallback(tableFilter: TableFilter) {
    return (event) => {
      const target = event.target as HTMLElement;
      if (target && target.classList.contains('dropdown-item')) {

        const tokens = target.innerText.split(':');
        if (tokens.length === 2) {
          handleEqualsEnum(tokens[0], tokens[1]);
        } else {
          handleEqualsInput(tokens[0]);
        }
      }
    }

    function handleEqualsInput(property: string) {
      if (tableFilter.mode == Mode.ADVANCED) {
        tableFilter.mainInput.value = tableFilter.createJsonPath({
          [property]: ['...']
        });
        Utils.checkAndMarkInInput(tableFilter.mainInput, '...');
      } else {
        tableFilter.mainInput.value = `${property}:`;
        tableFilter.mainInput.focus();
      }
    }

    function handleEqualsEnum(property: string, value: string) {
      if (tableFilter.mode === Mode.ADVANCED) {
        tableFilter.mainInput.value = tableFilter.createJsonPath({
          [property]: [value]
        });
      } else {
        tableFilter.addFilter(property, value);
      }
      tableFilter.filterChanged();
    }
  }

  private mainInputChangedCallback(tableFilter: TableFilter) {
    return (event: KeyboardEvent) => {
      if (event.key === 'Enter' || event.code === 'Enter') {
        switch (tableFilter.mode) {
          case Mode.BASIC:
              const result = /^(.*?):(.*)/gm.exec(tableFilter.mainInput.value.replace(/\s/g, ''));
              Utils.assert(result, 'Use "Key:Value1,Value2,..." to add a filter', tableFilter.mainInput);
              tableFilter.addFilter(result[1], result[2]);
              tableFilter.filterChanged();
              tableFilter.mainInput.value = '';
            break;
          case Mode.ADVANCED:
            try {
              let test = JSONPath({
                path: tableFilter.mainInput.value,
                json: [{test:'test'}],
              });
              tableFilter.filterChanged();
            }
            catch (error) {
              Utils.assert(false, error.message, tableFilter.mainInput);
            }
        }
      }
    }
  }

  private filterDropdown(tableFilter: TableFilter) {
    return (event) => {
      tableFilter.querySelectorAll('.dropdown-menu li').forEach((element: HTMLElement) => {
        const value = (event.target as HTMLInputElement).value.toLowerCase();
        element.hidden = element.textContent.toLowerCase().indexOf(value) < 0;  
      });
    }
  }

  private filterChanged() {
    switch(this.mode) {
      case Mode.BASIC:
        this.validatedFilter = this.createJsonPath(this.basicFilters);
        this.fillUIChips();
        break;
      case Mode.ADVANCED:
        this.validatedFilter = this.mainInput.value;
    }
    this.dispatchEvent(new CustomEvent('filterChange', {
        composed: true,
        detail: {
          filter: this.validatedFilter
        }
    }));
  }
}

customElements.define('table-filter', TableFilter);
