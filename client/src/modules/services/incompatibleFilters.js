/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function incompatibleFilters(filterData, view) {
  const bothExist = (arr, checkLevel) =>
    arr.every((val) =>
      filterData.some(({type, filterLevel}) => type === val && sameLevel(checkLevel, filterLevel))
    );

  return (
    bothExist(['completedInstancesOnly', 'runningInstancesOnly']) ||
    bothExist(['completedInstancesOnly', 'suspendedInstancesOnly']) ||
    bothExist(['canceledInstancesOnly', 'runningInstancesOnly']) ||
    bothExist(['canceledInstancesOnly', 'nonCanceledInstancesOnly']) ||
    bothExist(['canceledInstancesOnly', 'suspendedInstancesOnly']) ||
    bothExist(['nonSuspendedInstancesOnly', 'suspendedInstancesOnly']) ||
    bothExist(['endDate', 'runningInstancesOnly']) ||
    bothExist(['endDate', 'suspendedInstancesOnly']) ||
    ((view?.entity === 'flowNode' || view?.entity === 'userTask') &&
      (bothExist(['completedFlowNodesOnly', 'runningFlowNodesOnly']) ||
        bothExist(['canceledFlowNodesOnly', 'runningFlowNodesOnly']) ||
        bothExist(['completedOrCanceledFlowNodesOnly', 'runningFlowNodesOnly']) ||
        bothExist(['completedFlowNodesOnly', 'canceledFlowNodesOnly']))) ||
    bothExist(['doesNotIncludeIncident', 'includesOpenIncident']) ||
    bothExist(['doesNotIncludeIncident', 'includesResolvedIncident']) ||
    (view?.entity === 'incident' &&
      bothExist(['includesOpenIncident', 'includesResolvedIncident'], 'view'))
  );
}

function sameLevel(checkLevel, filterLevel) {
  if (checkLevel) {
    return checkLevel === filterLevel;
  }
  return true;
}
