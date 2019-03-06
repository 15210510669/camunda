import React from 'react';

import {Dropdown} from 'components';
import {reportConfig, getDataKeys} from 'services';
import {isChecked} from './service';

import './ReportDropdown.scss';

export default function ReportDropdown({
  type,
  field,
  value,
  xml,
  disabled,
  onChange,
  variables = {},
  previous = []
}) {
  const config = reportConfig[type];
  const options = config.options[field];

  const label = config.getLabelFor(options, value, xml) || 'Please Select...';

  return (
    <Dropdown label={label} className="ReportDropdown" disabled={disabled}>
      {Object.entries(options).map(([key, {data, label}]) => {
        const submenu = getDataKeys(data).find(key => Array.isArray(data[key]));
        const checked = isChecked(data, value);
        let disabled = !config.isAllowed(...previous, data);

        if (submenu) {
          let options = data[submenu];
          if (key.toLowerCase().includes('variable') && variables[key]) {
            if (variables[key].length === 0) {
              disabled = true;
            }

            options = variables[key].map(data => ({
              data,
              label: data.name
            }));
          }
          return (
            <Dropdown.Submenu key={key} label={label} checked={checked} disabled={disabled}>
              {options.map(({data: submenuData, label}, idx) => {
                const completeData = {...data, [submenu]: submenuData};
                const checked = isChecked(completeData, value);

                return (
                  <Dropdown.Option
                    key={idx}
                    checked={checked}
                    onClick={() => onChange(completeData)}
                  >
                    {label}
                  </Dropdown.Option>
                );
              })}
            </Dropdown.Submenu>
          );
        } else {
          return (
            <Dropdown.Option
              key={key}
              checked={checked}
              disabled={disabled}
              onClick={() => onChange(data)}
            >
              {label}
            </Dropdown.Option>
          );
        }
      })}
    </Dropdown>
  );
}
