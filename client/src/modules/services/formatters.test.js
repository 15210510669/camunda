/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  frequency as frequencyFormatter,
  duration as durationFormatter,
  convertDurationToObject,
  convertDurationToSingleNumber,
  convertToMilliseconds,
  getHighlightedText,
  camelCaseToLabel,
  formatReportResult,
  createDurationFormattingOptions,
  formatFileName,
} from './formatters';
const nbsp = '\u00A0';

describe('frequencyFormatter', () => {
  it('should do nothing for numbers < 1000', () => {
    expect(frequencyFormatter(4)).toBe('4');
    expect(frequencyFormatter(194)).toBe('194');
  });

  it('should handle zero well', () => {
    expect(frequencyFormatter(0)).toBe('0');
  });

  it('should format numbers', () => {
    expect(frequencyFormatter(6934)).toBe(new Intl.NumberFormat().format(6934));
    expect(frequencyFormatter(61934)).toBe(new Intl.NumberFormat().format(61934));
    expect(frequencyFormatter(761934)).toBe(new Intl.NumberFormat().format(761934));
    expect(frequencyFormatter(2349875982)).toBe(new Intl.NumberFormat().format(2349875982));
  });

  it('should use a precision', () => {
    expect(frequencyFormatter(123, 1)).toBe(new Intl.NumberFormat().format(100));
    expect(frequencyFormatter(12345, 2)).toBe(new Intl.NumberFormat().format(12) + ' thousand');
    expect(frequencyFormatter(12345, 9)).toBe(new Intl.NumberFormat().format(12.345) + ' thousand');
    expect(frequencyFormatter(12345678, 2)).toBe(new Intl.NumberFormat().format(12) + ' million');
    expect(frequencyFormatter(12345678, 4)).toBe(
      new Intl.NumberFormat().format(12.35) + ' million'
    );
  });

  it('should return -- for nondefined values', () => {
    expect(frequencyFormatter()).toBe('--');
    expect(frequencyFormatter('')).toBe('--');
    expect(frequencyFormatter(null)).toBe('--');
  });
});

describe('durationFormatter', () => {
  it('should format ms input into human readable string', () => {
    const time = 27128;

    expect(durationFormatter(time)).toBe(`27s${nbsp}128ms`);
  });

  it('should handle zero well', () => {
    expect(durationFormatter(0)).toBe('0ms');
  });

  it('should single unit well', () => {
    expect(durationFormatter(5 * 60 * 60 * 1000)).toBe('5h');
  });

  it('should handle single millisecond durations', () => {
    expect(durationFormatter(1)).toBe('1ms');
  });

  it('should handle millisecond durations that are below 1', () => {
    expect(durationFormatter({value: 0.2, unit: 'millis'})).toBe('0.2ms');
  });

  it('should not floor millisecond durations only', () => {
    expect(durationFormatter({value: 1.3, unit: 'millis'})).toBe('1.3ms');
    expect(durationFormatter({value: 1.2, unit: 'seconds'})).toBe(`1s${nbsp}200ms`);
  });

  it('should handle a time object', () => {
    expect(durationFormatter({value: 14, unit: 'seconds'})).toBe('14s');
  });

  it('should normalize a time object', () => {
    expect(durationFormatter({value: 15, unit: 'days'})).toBe(`2wk${nbsp}1d`);
  });

  it('should use a precision', () => {
    expect(durationFormatter(123456789, 2)).toBe(`1 day${nbsp}10 hours`);
    expect(durationFormatter(123456789, 4)).toBe(
      `1 day${nbsp}10 hours${nbsp}17 minutes${nbsp}37 seconds`
    );
  });

  it('should return -- for nondefined values', () => {
    expect(durationFormatter()).toBe('--');
    expect(durationFormatter('')).toBe('--');
    expect(durationFormatter(null)).toBe('--');
  });
});

describe('convertDurationToObject', () => {
  it('should return an object with value and unit', () => {
    const result = convertDurationToObject(123);

    expect(result.unit).toBeDefined();
    expect(result.value).toBeDefined();
  });

  it('should convert a millisecond value', () => {
    expect(convertDurationToObject(123)).toEqual({value: '123', unit: 'millis'});
    expect(convertDurationToObject(4 * 60 * 1000)).toEqual({value: '4', unit: 'minutes'});
    expect(convertDurationToObject(1000)).toEqual({value: '1', unit: 'seconds'});
    expect(convertDurationToObject(1001)).toEqual({value: '1001', unit: 'millis'});
  });
});

describe('convertDurationToSingleNumber', () => {
  it('should return simple numbers unprocessed', () => {
    expect(convertDurationToSingleNumber(123)).toBe(123);
  });

  it('should convert duration objects to millis', () => {
    expect(convertDurationToSingleNumber({value: '123', unit: 'millis'})).toBe(123);
    expect(convertDurationToSingleNumber({value: '2', unit: 'minutes'})).toBe(2 * 60 * 1000);
    expect(convertDurationToSingleNumber({value: '1.5', unit: 'seconds'})).toBe(1500);
  });
});

describe('convertToMilliseconds', () => {
  expect(convertToMilliseconds(5, 'seconds')).toBe(5000);
  expect(convertToMilliseconds(2, 'months')).toBe(5184000000);
  expect(convertToMilliseconds(3, 'hours')).toBe(10800000);
  expect(convertToMilliseconds(100, 'millis')).toBe(100);
});

describe('camelCaseToLabel', () => {
  expect(camelCaseToLabel('fooBar')).toBe('Foo Bar');
  expect(camelCaseToLabel('startDate')).toBe('Start Date');
});

describe('getHighlightedText', () => {
  it('Should wrap the highlighted text in a span and give it textBold class', () => {
    const results = getHighlightedText('test text', 'text');
    expect(results[1].props.children).toBe('text');
    expect(results[1].props.className).toBe('textBold');
  });

  it('Should return the same text as string if the highlight is empty', () => {
    const results = getHighlightedText('test text', '');
    expect(results).toBe('test text');
  });

  it('the regex should match only from the start of the text if specified', () => {
    const notMatch = getHighlightedText('test text', 'text', true);
    expect(notMatch.length).toBe(1);
    const results = getHighlightedText('test text', 'test', true);
    expect(results[1].props.children).toBe('test');
    expect(results[1].props.className).toBe('textBold');
  });
});

const exampleDurationReport = {
  name: 'report A',
  combined: false,
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      property: 'foo',
    },
    groupBy: {
      type: 'startDate',
      value: {
        unit: 'day',
      },
    },
    visualization: 'table',
    configuration: {sorting: null},
  },
  result: {
    instanceCount: 100,
    data: [
      {key: '2015-03-25T12:00:00Z', label: '2015-03-25T12:00:00Z', value: 2},
      {key: '2015-03-26T12:00:00Z', label: '2015-03-26T12:00:00Z', value: 3},
    ],
  },
};

jest.mock('services', () => {
  return {
    reportConfig: {
      getLabelFor: () => 'foo',
      view: {foo: {data: 'foo', label: 'viewfoo'}},
      groupBy: {
        foo: {data: 'foo', label: 'groupbyfoo'},
      },
    },
  };
});

it('should adjust dates to units', () => {
  const formatedResult = formatReportResult(
    exampleDurationReport.data,
    exampleDurationReport.result.data
  );
  expect(formatedResult).toEqual([
    {key: '2015-03-25T12:00:00Z', label: '2015-03-25', value: 2},
    {key: '2015-03-26T12:00:00Z', label: '2015-03-26', value: 3},
  ]);
});

it('should adjust groupby Start Date option to unit', () => {
  const specialExampleReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      groupBy: {
        type: 'startDate',
        value: {unit: 'month'},
      },
    },
    result: {
      ...exampleDurationReport.result,
      data: [exampleDurationReport.result.data[0]],
    },
  };
  const formatedResult = formatReportResult(
    specialExampleReport.data,
    specialExampleReport.result.data
  );
  expect(formatedResult).toEqual([{key: '2015-03-25T12:00:00Z', label: 'Mar 2015', value: 2}]);
});

it('should adjust groupby Variable Date option to unit', () => {
  const specialExampleReport = {
    ...exampleDurationReport,
    data: {
      ...exampleDurationReport.data,
      groupBy: {
        type: 'variable',
        value: {type: 'Date'},
      },
    },
  };
  const formatedResult = formatReportResult(
    specialExampleReport.data,
    specialExampleReport.result.data
  );

  expect(formatedResult[0].label).not.toContain('2015-03-25T');
  expect(formatedResult[0].label).toContain('2015-03-25 ');
});

describe('automatic interval selection', () => {
  const autoData = {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      property: 'foo',
    },
    groupBy: {
      type: 'startDate',
      value: {
        unit: 'automatic',
      },
    },
    visualization: 'table',
    configuration: {sorting: null},
  };

  it('should use seconds when interval is less than hour', () => {
    const result = [
      {key: '2017-12-27T14:21:56.000', label: '2017-12-27T14:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2017-12-27 14:21:56', value: 2},
      {key: result[1].key, label: '2017-12-27 14:21:57', value: 3},
    ]);
  });

  it('should use hours when interval is less than a day', () => {
    const result = [
      {key: '2017-12-27T13:21:56.000', label: '2017-12-27T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2017-12-27 13:00:00', value: 2},
      {key: result[1].key, label: '2017-12-27 14:00:00', value: 3},
    ]);
  });

  it('should use day when interval is less than a month', () => {
    const result = [
      {key: '2017-12-20T13:21:56.000', label: '2017-12-20T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2017-12-20', value: 2},
      {key: result[1].key, label: '2017-12-27', value: 3},
    ]);
  });

  it('should use month when interval is less than a year', () => {
    const result = [
      {key: '2017-05-27T13:21:56.000', label: '2017-05-27T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: 'May 2017', value: 2},
      {key: result[1].key, label: 'Dec 2017', value: 3},
    ]);
  });

  it('should use year when interval is greater than/equal a year', () => {
    const result = [
      {key: '2015-12-27T13:21:56.000', label: '2015-12-27T13:21:56.000', value: 2},
      {key: '2017-12-27T14:21:57.000', label: '2017-12-27T14:21:57.000', value: 3},
    ];

    const formatedResult = formatReportResult(autoData, result);

    expect(formatedResult).toEqual([
      {key: result[0].key, label: '2015 ', value: 2},
      {key: result[1].key, label: '2017 ', value: 3},
    ]);
  });
});

it('should show nice ticks for duration formats on the y axis', () => {
  const maxValue = 7 * 24 * 60 * 60 * 1000;

  const config = createDurationFormattingOptions(0, maxValue);

  expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
  expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('3d');
});

describe('File name formatting', () => {
  const formattedFileName = formatFileName('/*File name:');
  expect(formattedFileName).toBe('File-name');

  const anotherFileName = formatFileName('<another?|name>');
  expect(anotherFileName).toBe('anothername');
});
