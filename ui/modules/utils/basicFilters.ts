/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

export interface FilterListener {
  filterChanged: () => void;
}

export enum FilterType {
  PROP_LIKE,
  PROP_EQ,
}

export class Term {
  key: string;
  values: Array<string>;
  type: FilterType
  keyDisplay: string;
  
  constructor(type: FilterType, value: string, key = '@', keyDisplay?: string) {
    console.assert(!(key === '@' && type !== FilterType.PROP_LIKE))
    this.type = type;
    this.key = key;
    this.values = [value];
    this.keyDisplay = keyDisplay
  }

  add(values: string[]) {
    this.values = [...new Set([
      ...values,
      ...this.values
    ])];
  }

  toJsonPath(): string {
    switch(this.type) {
      case FilterType.PROP_EQ:
        return this.values.map((x) => `@.${this.key}=="${x}"`).join('||');
      case FilterType.PROP_LIKE:
        if (this.key === '@') {
          return `/${this.values[0].toString().replace(/\//g, '\\/')}/.test(JSON.stringify(@))`;
        } else {
          return this.values.map((x) => `/${x.replace(/\//g, '\\/')}/.test(@.${this.key})`).join('||');
        }
    }
  }

  public static fromString(input: string): Term {
    const result = /^([^:~]+)(:|~)?(.*)/gm.exec(input.replace(/\s/g, ''));

    if (result[2] === ':') {
      return new Term(FilterType.PROP_EQ, result[3], result[1]);
    } else if (result[2] === '~') {
      return new Term(FilterType.PROP_LIKE, result[3], result[1]);
    } else {
      return new Term(FilterType.PROP_LIKE, result[1]);
    }
  }

  public static fromJSON(input: {type: FilterType, values: string[], key: string, keyDisplay: string}) {
    console.assert(input.values.length === 1);
    return new Term(input.type, input.values[0], input.key, input.keyDisplay);
  }

  toString(): string {
    switch (this.type) {
      case FilterType.PROP_EQ:
        return `${this.keyDisplay ?? this.key}:${this.values.toString().replace(/\\/g, '')}`;
      case FilterType.PROP_LIKE:
        return this.key === '@' ?
          (this.values[0]) :
          (`${this.keyDisplay ?? this.key}~${this.values.toString().replace(/\\/g, '')}`);
    }
  }
}

export class BasicFilters {

  terms: Array<Term> = [];
  // allLike: string;
  onFilterChange: FilterListener;

  addPropEq = (key: string, value: string) => {
    this.addOrUpdate(new Term(FilterType.PROP_EQ, value, key));
    return this;
  }

  addPropLike = (key: string, value: string) => {
    this.addOrUpdate(new Term(FilterType.PROP_LIKE, value, key));
    return this;
  }
  setAllLike(value: string) {
    this.addOrUpdate(new Term( FilterType.PROP_LIKE, value));
    return this;
  }

  addFromString = (input: string) => {
    let t = Term.fromString(input);
    this.addOrUpdate(t);
    return this;
  }
  
  addOrUpdate = (term: Term) => {
    let existing = this.terms.find((t) => (t.type === term.type && t.key === term.key));
    if (existing) {
      existing.add(term.values);
    } else {
      this.terms.push(term);
    }
    return this;
  }

  clear() {
    this.terms = [];
  }

  getAllUIs = () => {
    return this.terms.map((t) => this.createChipUIForTerm(t));
  }

  private createChipUIForTerm(term: Term) {
    const chip = document.createElement('span');
    chip.classList.add('badge', 'rounded-pill', 'bg-info', 'mb-1', 'me-1');
    chip.innerText = term.toString();
    const button = document.createElement('button');
    button.type = 'button';
    button.classList.add('btn-close');
    button.style.fontSize = '0.75em';
    button.setAttribute('data-key', term.key);
    button.setAttribute('data-type', FilterType[term.type]);
    button.onclick = this.deleteFilterCallback;
    chip.appendChild(button);
    return chip;
  }
  private deleteFilterCallback = (event: PointerEvent) => {
    const key: string = (event.target as HTMLButtonElement).getAttribute('data-key');
    const type: FilterType = FilterType[(event.target as HTMLButtonElement).getAttribute('data-type')];
    this.terms = this.terms.filter((t) => t.key !== key || t.type !== type);
    this.onFilterChange && this.onFilterChange.filterChanged();
  }

  createJsonPath() {
    let ands = this.terms.map((term) => term.toJsonPath());

    if (ands.length > 1) {
      return `$[?(${ands.map((part) => `(${part})`).join('&&')})]`;
    } else if (ands.length > 0) {
      return `$[?(${ands})]`;
    } else {
      return null;
    };
  }
};
