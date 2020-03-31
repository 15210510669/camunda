/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {MetricPanel} from './MetricPanel';
import * as Styled from './styled.js';

import {
  countStoreEmpty,
  countStoreComplete,
  countStoreWithoutIncidents,
  countStoreLoading,
} from './MetricPanel.setup';

jest.mock('modules/utils/bpmn');

describe('MetricPanel', () => {
  describe('Title', () => {
    it('should render title containing instances count', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreComplete} />);
      const titleNode = node.find(Styled.Title);

      // then
      expect(titleNode).toExist();
      expect(titleNode.text()).toEqual(
        `${countStoreComplete.running} Running Instances in total`
      );
    });

    it('should render title during loading without instances count', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreLoading} />);
      const titleNode = node.find(Styled.Title);

      // then
      expect(titleNode).toExist();
      expect(titleNode.text()).toEqual(`Running Instances in total`);
    });

    it('should render correct link (if instances)', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreComplete} />);
      const titleNode = node.find(Styled.Title);

      // then
      expect(titleNode.props().to).toEqual(
        '/instances?filter={"active":true,"incidents":true}'
      );
    });

    it('should render correct link (if no instances)', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreEmpty} />);
      const titleNode = node.find(Styled.Title);

      // then
      expect(titleNode.props().to).toEqual(
        '/instances?filter={"active":true,"incidents":true,"completed":true,"canceled":true}'
      );
    });
  });

  describe('Label', () => {
    it('should pass correct link to incident label (if incidents)', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreComplete} />);
      const IncidentsLabelNode = node.find(Styled.Label).at(0);

      // then
      expect(IncidentsLabelNode.props().to).toEqual(
        '/instances?filter={"incidents":true}'
      );
    });

    it('should pass correct link to incident label (if no incidents)', () => {
      // when
      const node = shallow(
        <MetricPanel countStore={countStoreWithoutIncidents} />
      );
      const IncidentsLabelNode = node.find(Styled.Label).at(0);

      // then
      expect(IncidentsLabelNode.props().to).toEqual(
        '/instances?filter={"incidents":true}'
      );
    });

    it('should pass correct link to active instances label (if instances)', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreComplete} />);
      const ActiveInstancesLabelNode = node.find(Styled.Label).at(1);

      // then
      expect(ActiveInstancesLabelNode.props().to).toEqual(
        '/instances?filter={"active":true}'
      );
    });

    it('should pass correct link to active instances label (if no instances)', () => {
      // when
      const node = shallow(<MetricPanel countStore={countStoreEmpty} />);
      const ActiveInstancesLabelNode = node.find(Styled.Label).at(1);

      // then
      expect(ActiveInstancesLabelNode.props().to).toEqual(
        '/instances?filter={"active":true}'
      );
    });
  });

  it('should pass panel data to InstancesBar', () => {
    const node = shallow(<MetricPanel countStore={countStoreComplete} />);

    const InstancesBarNode = node.find(Styled.InstancesBar);

    expect(InstancesBarNode).toExist();

    const InstancesBarProps = InstancesBarNode.props();

    expect(InstancesBarProps.activeCount).toEqual(countStoreComplete.active);
    expect(InstancesBarProps.incidentsCount).toEqual(
      countStoreComplete.withIncidents
    );
  });
});
