import {isValid, addDays, startOfDay, addMinutes, format} from 'date-fns';

/**
 * Returns a query string for the filter objects
 * removes keys with empty values (null, "", []) so that they don't appear in URL
 */
export function getFilterQueryString(filter = {}) {
  const cleanedFilter = Object.entries(filter).reduce((obj, [key, value]) => {
    return !!value && value.length !== 0 ? {...obj, [key]: value} : obj;
  }, {});

  return `?filter=${JSON.stringify(cleanedFilter)}`;
}

/**
 * For a given date field's value returns the corresponding url options for filtering
 * Returns an object of two values [name]dateBefore and [name]dateAfter
 * where name is oneOf['starDate', 'endDate']
 */
const parseDate = (value, name) => {
  let date = new Date(value);
  const isValidDate = isValid(date);
  let dateAfter, dateBefore;
  // enforce no comma in the timezone
  const formatWithTimezone = 'YYYY-MM-DDTHH:mm:ss.SSSZZ';

  if (value === '') {
    return {
      [`${name}After`]: null,
      [`${name}Before`]: null
    };
  }

  if (!isValidDate) {
    return null;
  }

  // temporary condition to check for presence of time in user input
  // as we can't decide based on a string
  const hasTime = value.indexOf(':') !== -1;

  dateAfter = hasTime ? date : startOfDay(date);
  dateBefore = hasTime ? addMinutes(date, 1) : addDays(date, 1);

  return {
    [`${name}After`]: format(dateAfter, formatWithTimezone),
    [`${name}Before`]: format(dateBefore, formatWithTimezone)
  };
};

/**
 * Collection of parsers for filter field values
 * we used this parser before making a call to backend with the current filters
 */
export const fieldParser = {
  ids: (name, value) => {
    // split by space, comma, tab or return key
    return {[name]: value.split(/[ ,\t\n]+/).filter(Boolean)};
  },
  startDate: (name, value) => {
    return parseDate(value, 'startDate');
  },
  endDate: (name, value) => {
    return parseDate(value, 'endDate');
  }
};

function defaultFieldParser(name, value) {
  return {[name]: value};
}

/**
 * Adds running or finished additional payload,
 * they are required when fetching the instances by state
 */
export function getInstanceStatePayload(filter) {
  const {active, incidents, completed, canceled} = filter;
  const result = {};

  if (active || incidents) {
    result.running = true;
  }

  if (completed || canceled) {
    result.finished = true;
  }

  return result;
}

/**
 * Before fetching the instances for the ListView
 * the filter field values need to be parsed
 * @param {Object} filter
 * @return {Object}
 */
export function parseFilterForRequest(filter) {
  let parsedFilter = {...getInstanceStatePayload(filter)};

  for (let key in filter) {
    const value = filter[key];
    const parsedField = fieldParser[key]
      ? fieldParser[key](key, value)
      : defaultFieldParser(key, value);

    parsedFilter = {
      ...parsedFilter,
      ...parsedField
    };
  }

  return {
    ...trimmFilter(parsedFilter)
  };
}

export function getWorkflowByVersion(workflow, version) {
  if (!workflow || version === 'all') return {};
  return workflow.workflows.find(item => {
    return String(item.version) === String(version);
  });
}

function trimmFilter(filter) {
  let newFilter = {};

  for (let key in filter) {
    const value = filter[key];
    newFilter[key] = typeof value === 'string' ? value.trim() : value;
  }

  return newFilter;
}

/**
 * For using a filter in a request we replace filter.workflow & filter.version
 * with the corresponding workflowIds:[..] field
 * @param {Object} filter
 * @param {Object} allWorkflows all the available workflows
 */
export function getFilterWithWorkflowIds(filter = {}, allWorkflows = {}) {
  const {workflow, version, ...otherFields} = filter;
  let workflowIds = [];
  let newFilter = {...otherFields};

  if (!Boolean(workflow) && !Boolean(version)) {
    return otherFields;
  }

  if (version === 'all') {
    allWorkflows[workflow].workflows.forEach(item => {
      workflowIds.push(item.id);
    });
    newFilter.workflowIds = workflowIds;
  } else {
    const workflowByVersion = getWorkflowByVersion(
      allWorkflows[workflow],
      version
    );

    if (Boolean(workflowByVersion)) {
      workflowIds.push(workflowByVersion.id);
      newFilter.workflowIds = workflowIds;
    }
  }

  return {...newFilter};
}
