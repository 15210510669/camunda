/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rgba} from 'polished';

import darkZeebraStripe from 'modules/components/ZeebraStripe/tree-view-bg-dark.png';
import lightZeebraStripe from 'modules/components/ZeebraStripe/tree-view-bg-light.png';
import incidentsOverlayDarkBackgroundImage from './images/bg-dark@2x.png';
import incidentsOverlayLightBackgroundImage from './images/bg-light@2x.png';

const SEMANTIC_COLORS = {
  selections: '#4d90ff',
  allIsWell: '#10d070',
  incidentsAndErrors: '#ff3d3d',
  filtersAndWarnings: '#ffa533',
  focusOuter: '#8cb7ff',
  badge01: '#d7d7d9',
  badge02: '#88888d',
  primaryButton01: '#a2c5ff',
  primaryButton02: '#80b0ff',
  primaryButton03: '#3c85ff',
  primaryButton04: '#1a70ff',
  primaryButton05: '#005df7',
  black: '#000',
  white: '#fff',
  grey: '#dedede',
  outlineError: '#ffafaf',
  transparent: 'transparent',
} as const;
const DARK_COLORS = {
  ...SEMANTIC_COLORS,
  ui01: '#1c1f23',
  ui02: '#313238',
  ui03: '#393a41',
  ui04: '#45464e',
  ui05: '#5b5e63',
  ui06: '#6d7076',
  itemOdd: '#313238',
  itemEven: '#37383e',
  selectedOdd: '#3a527d',
  selectedEven: '#3e5681',
  menuActive: '#393a42',
  linkDefault: '#d9eaff',
  linkHover: '#c8e1ff',
  linkActive: '#eaf3ff',
  linkVisited: '#c889fe',
  focusInner: '#2B7BFF',
  button01: '#6b6f74',
  button02: '#7f8289',
  button03: '#34353a',
  button04: '#3c3d43',
  button05: '#646670',
  button06: '#2c2d31',
  button07: '#73777e',
  label: '#4a4c51',
  pillHover: '#767a80',
  treeHover: '#4e4f55',
  logo: '#f8f8f8',
} as const;
const LIGHT_COLORS = {
  ...SEMANTIC_COLORS,
  ui01: '#f2f3f5',
  ui02: '#f7f8fa',
  ui03: '#b0bac7',
  ui04: '#fdfdfe',
  ui05: '#d8dce3',
  ui06: '#62626e',
  itemOdd: '#fdfdfe',
  itemEven: '#f9fafc',
  selectedOdd: '#bfd6fe',
  selectedEven: '#bdd4fd',
  menuActive: '#bcc6d2',
  linkDefault: '#346ac4',
  linkHover: '#4b7ccf',
  linkActive: '#29549c',
  linkVisited: '#a846fe',
  focusInner: '#c8e1ff',
  button01: '#cdd4df',
  button02: '#9ea9b7',
  button03: '#88889a',
  button04: '#f1f2f5',
  button05: '#e7e9ee',
  button06: '#d3d6e0',
  label: '#edeff3',
  treeHover: '#e7e9ee',
  logo: '#666666',
} as const;

const theme = Object.freeze({
  dark: {
    cmTheme: 'Dark',
    colors: {
      ...DARK_COLORS,
      metricPanel: {
        skeletonBar: {
          backgroundColor: SEMANTIC_COLORS.badge02,
        },
      },
      panelListItem: {
        active: {
          borderColor: rgba(DARK_COLORS.ui05, 0.7),
        },
        hover: {
          borderColor: DARK_COLORS.ui05,
        },
      },
      dashboard: {
        panelStyles: {
          borderColor: DARK_COLORS.ui04,
          backgroundColor: DARK_COLORS.ui02,
        },
        metricPanelWrapper: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
        },
        tileTitle: {
          color: SEMANTIC_COLORS.white,
        },
        skeleton: {
          block: {
            backgroundColor: SEMANTIC_COLORS.badge02,
          },
        },
        message: {
          default: {
            color: rgba(SEMANTIC_COLORS.white, 0.8),
          },
          error: {
            color: rgba(SEMANTIC_COLORS.incidentsAndErrors, 0.9),
          },
          success: {
            color: rgba(SEMANTIC_COLORS.allIsWell, 0.9),
          },
        },
      },
      header: {
        navElements: {
          borderColor: rgba('#f6fcfb', 0.5),
        },
        user: {
          backgroundColor: rgba(SEMANTIC_COLORS.badge02, 0.2),
        },
        details: {
          borderColor: rgba('#f6fcfb', 0.5),
        },
        skeleton: {
          backgroundColor: rgba(SEMANTIC_COLORS.badge02, 0.2),
        },
        color: SEMANTIC_COLORS.white,
      },
      variablesPanel: {
        color: rgba(SEMANTIC_COLORS.white, 0.8),
      },
      variables: {
        skeleton: {
          row: {
            borderColor: DARK_COLORS.ui04,
          },
          borderColor: DARK_COLORS.ui04,
          backgroundColor: DARK_COLORS.ui02,
        },
        placeholder: {
          color: SEMANTIC_COLORS.grey,
        },
        td: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
        },
        tHead: {
          borderColor: DARK_COLORS.ui04,
          backgroundColor: DARK_COLORS.ui02,
        },
        editButton: {
          disabled: {
            color: LIGHT_COLORS.ui02,
          },
        },
        icons: {
          color: LIGHT_COLORS.ui02,
        },
        codeLine: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          before: {
            color: SEMANTIC_COLORS.white,
          },
        },
        linesSeparator: {
          backgroundColor: DARK_COLORS.ui02,
        },
        variablesTable: {
          tr: {
            borderColor: DARK_COLORS.ui04,
            backgroundColor: rgba(DARK_COLORS.ui05, 0.4),
          },
        },
        color: rgba(SEMANTIC_COLORS.white, 0.8),
      },
      bottomPanel: {
        borderColor: DARK_COLORS.ui04,
      },
      flowNodeInstanceLog: {
        borderColor: DARK_COLORS.ui04,
        color: rgba(SEMANTIC_COLORS.white, 0.9),
      },
      flowNodeInstancesTree: {
        bar: {
          nodeIcon: {
            color: SEMANTIC_COLORS.white,
          },
          container: {
            borderColor: DARK_COLORS.ui04,
            selected: {
              borderColor: DARK_COLORS.ui04,
            },
          },
          nodeName: {
            borderColor: DARK_COLORS.ui04,
            color: SEMANTIC_COLORS.white,
            selected: {
              borderColor: rgba(SEMANTIC_COLORS.white, 0.25),
            },
          },
        },
        foldable: {
          summaryLabel: {
            borderColor: DARK_COLORS.ui04,
            backgroundColor: DARK_COLORS.ui04,
          },
        },
        timeStampLabel: {
          color: SEMANTIC_COLORS.white,
          backgroundColor: rgba(LIGHT_COLORS.ui02, 0.15),
        },
        connectionDot: {
          color: '#65666d',
        },
        nodeDetails: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          selected: {
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
        },
        ul: {
          backgroundColor: '#65666d',
        },
      },
      incidentsFilter: {
        filtersWrapper: {
          backgroundColor: DARK_COLORS.ui03,
        },
        label: {
          color: SEMANTIC_COLORS.white,
          backgroundColor: DARK_COLORS.ui04,
        },
        moreDropdown: {
          dropdownToggle: {
            borderColor: DARK_COLORS.ui06,
            color: DARK_COLORS.ui02,
          },
          item: {
            borderColor: DARK_COLORS.ui04,
          },
        },
        buttonsWrapper: {
          backgroundColor: DARK_COLORS.ui04,
        },
      },
      incidentsTable: {
        index: {
          color: SEMANTIC_COLORS.white,
        },
        firstTh: {
          before: {
            backgroundColor: DARK_COLORS.ui04,
          },
          after: {
            backgroundColor: DARK_COLORS.ui04,
          },
        },
        incidentTr: {
          hover: {
            backgroundColor: DARK_COLORS.treeHover,
          },
        },
        fake: {
          backgroundColor: 'yellow',
          before: {
            borderColor: DARK_COLORS.ui04,
            backgroundColor: DARK_COLORS.ui03,
          },
        },
      },
      incidentsOverlay: {
        backgroundColor: DARK_COLORS.ui02,
      },
      incidentsWrapper: {
        borderColor: DARK_COLORS.ui04,
      },
      instanceHeader: {
        backgroundColor: DARK_COLORS.ui02,
        borderColor: DARK_COLORS.ui04,
      },
      topPanel: {
        pseudoBorder: {
          borderColor: DARK_COLORS.ui04,
        },
        pane: {
          backgroundColor: DARK_COLORS.ui02,
        },
      },
      instance: {
        section: {
          borderColor: DARK_COLORS.ui04,
        },
        flowNodeInstanceLog: {
          borderColor: DARK_COLORS.ui04,
          color: rgba(SEMANTIC_COLORS.white, 0.9),
        },
        pseudoBorder: {
          borderColor: DARK_COLORS.ui04,
        },
        splitPaneTop: {
          backgroundColor: DARK_COLORS.ui02,
        },
      },
      emptyMessage: {
        color: SEMANTIC_COLORS.grey,
      },
      columnHeader: {
        color: SEMANTIC_COLORS.white,
      },
      list: {
        selectionStatusIndicator: {
          borderColor: DARK_COLORS.ui04,
        },
      },
      createOperationDropdown: {
        dropdownButtonStyles: {
          color: LIGHT_COLORS.ui04,
        },
      },
      paginator: {
        page: {
          color: SEMANTIC_COLORS.white,
          backgroundColor: DARK_COLORS.ui04,
          borderColor: DARK_COLORS.ui05,
          active: {
            color: SEMANTIC_COLORS.white,
            backgroundColor: rgba(SEMANTIC_COLORS.selections, 0.9),
            borderColor: '#007dff',
          },
          disabled: {
            backgroundColor: DARK_COLORS.button03,
            color: rgba(SEMANTIC_COLORS.white, 0.4),
          },
        },
      },
      operationsEntry: {
        entry: {
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          borderColor: DARK_COLORS.ui04,
          isRunning: {
            backgroundColor: DARK_COLORS.ui03,
          },
        },
        iconStyle: {
          color: SEMANTIC_COLORS.white,
        },
      },
      operationsPanel: {
        skeleton: {
          entry: {
            backgroundColor: DARK_COLORS.ui03,
            color: rgba(SEMANTIC_COLORS.white, 0.9),
            borderColor: DARK_COLORS.ui04,
          },
        },
        operationsList: {
          borderColor: DARK_COLORS.ui04,
        },
        emptyMessage: {
          borderColor: DARK_COLORS.ui04,
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          backgroundColor: DARK_COLORS.ui03,
        },
      },
      disclaimer: {
        container: {
          color: rgba(SEMANTIC_COLORS.white, 0.7),
        },
      },
      login: {
        logo: {
          color: SEMANTIC_COLORS.white,
        },
        loginTitle: {
          color: SEMANTIC_COLORS.white,
        },
      },
      modules: {
        badge: {
          filters: {
            color: DARK_COLORS.ui02,
          },
          default: {
            backgroundColor: LIGHT_COLORS.ui05,
            color: DARK_COLORS.ui04,
          },
        },
        button: {
          secondary: {
            backgroundColor: DARK_COLORS.button03,
            borderColor: DARK_COLORS.ui05,
            color: LIGHT_COLORS.ui02,
            hover: {
              backgroundColor: DARK_COLORS.button04,
              borderColor: DARK_COLORS.button05,
              color: LIGHT_COLORS.ui02,
            },
            active: {
              borderColor: DARK_COLORS.button07,
              color: LIGHT_COLORS.ui02,
            },
            disabled: {
              backgroundColor: DARK_COLORS.button03,
              color: rgba(LIGHT_COLORS.ui02, 0.5),
            },
          },
          primary: {
            color: LIGHT_COLORS.ui04,
            disabled: {
              color: rgba(LIGHT_COLORS.ui04, 0.8),
            },
          },
          main: {
            color: LIGHT_COLORS.ui02,
            borderColor: DARK_COLORS.ui06,
            hover: {
              backgroundColor: DARK_COLORS.button01,
              borderColor: DARK_COLORS.button02,
            },
            focus: {
              borderColor: DARK_COLORS.ui06,
            },
            active: {
              backgroundColor: DARK_COLORS.ui04,
              borderColor: DARK_COLORS.ui05,
            },
            disabled: {
              color: rgba(LIGHT_COLORS.ui02, 0.5),
              backgroundColor: DARK_COLORS.button03,
              borderColor: DARK_COLORS.ui05,
            },
          },
        },
        checkbox: {
          label: {
            color: '#ececec',
          },
          customCheckbox: {
            before: {
              borderColor: '#bebec0',
              backgroundColor: DARK_COLORS.ui02,
              selection: {
                backgroundColor: SEMANTIC_COLORS.selections,
              },
            },
            after: {
              borderColor: SEMANTIC_COLORS.white,
              selection: {
                borderColor: SEMANTIC_COLORS.white,
              },
            },
          },
        },
        codeModal: {
          codeEditor: {
            borderColor: DARK_COLORS.ui02,
            backgroundColor: DARK_COLORS.ui01,
            pre: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
              before: {
                color: SEMANTIC_COLORS.white,
              },
            },
          },
        },
        collapsablePanel: {
          collapsable: {
            backgroundColor: DARK_COLORS.ui03,
          },
          expandButton: {
            backgroundColor: DARK_COLORS.ui03,
            color: SEMANTIC_COLORS.white,
          },
        },
        collapseButton: {
          borderColor: DARK_COLORS.ui04,
          icons: {
            color: SEMANTIC_COLORS.white,
            active: {
              color: SEMANTIC_COLORS.white,
            },
          },
        },
        copyright: {
          color: SEMANTIC_COLORS.white,
        },
        diagram: {
          popoverOverlay: {
            arrowStyle: {
              before: {
                borderColor: DARK_COLORS.ui06,
              },
              after: {
                borderColor: DARK_COLORS.ui04,
              },
            },
            popOver: {
              backgroundColor: DARK_COLORS.ui04,
              color: SEMANTIC_COLORS.white,
              borderColor: DARK_COLORS.ui06,
            },
            codeLine: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
              before: {
                color: SEMANTIC_COLORS.white,
              },
            },
            linesSeparator: {
              backgroundColor: DARK_COLORS.ui02,
            },
          },
          statisticOverlay: {
            statistic: {
              active: {
                backgroundColor: SEMANTIC_COLORS.allIsWell,
                color: SEMANTIC_COLORS.white,
              },
              incidents: {
                backgroundColor: SEMANTIC_COLORS.incidentsAndErrors,
                color: SEMANTIC_COLORS.white,
              },
              completed: {
                backgroundColor: SEMANTIC_COLORS.badge02,
                color: SEMANTIC_COLORS.white,
              },
              canceled: {
                backgroundColor: SEMANTIC_COLORS.badge01,
                color: DARK_COLORS.ui04,
              },
            },
          },
          outline: {
            fill: rgba(DARK_COLORS.selectedOdd, 0.5),
          },
          defaultFillColor: DARK_COLORS.ui02,
          defaultStrokeColor: SEMANTIC_COLORS.grey,
        },
        dropdown: {
          menu: {
            pointerBody: {
              borderColor: DARK_COLORS.ui04,
            },
            pointerShadow: {
              borderColor: DARK_COLORS.ui06,
            },
            ul: {
              borderColor: DARK_COLORS.ui06,
              backgroundColor: DARK_COLORS.ui04,
              color: SEMANTIC_COLORS.white,
            },
            topPointer: {
              borderColor: DARK_COLORS.ui06,
            },
            bottomPointer: {
              borderColor: DARK_COLORS.ui06,
            },
            li: {
              borderColor: DARK_COLORS.ui06,
            },
          },
          option: {
            borderColor: DARK_COLORS.ui06,
            optionButton: {
              disabled: {
                color: rgba(SEMANTIC_COLORS.white, 0.6),
              },
              default: {
                color: rgba(SEMANTIC_COLORS.white, 0.9),
              },
              hover: {
                backgroundColor: DARK_COLORS.ui06,
              },
            },
          },
          subMenu: {
            subMenuButton: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
              hover: {
                backgroundColor: DARK_COLORS.ui06,
              },
            },
            ul: {
              backgroundColor: DARK_COLORS.ui04,
              color: SEMANTIC_COLORS.white,
              borderColor: DARK_COLORS.ui06,
            },
            li: {
              borderColor: DARK_COLORS.ui06,
            },
          },
          subOption: {
            optionButton: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
              hover: {
                backgroundColor: DARK_COLORS.ui06,
              },
            },
          },
          button: {
            default: {
              color: rgba(SEMANTIC_COLORS.white, 0.9),
            },
            disabled: {
              color: rgba(SEMANTIC_COLORS.white, 0.6),
            },
          },
        },
        emptyPanel: {
          color: SEMANTIC_COLORS.white,
        },
        flowNodeIcon: {
          color: SEMANTIC_COLORS.white,
        },
        iconButton: {
          icon: {
            default: {
              svg: {
                color: SEMANTIC_COLORS.white,
              },
              before: {
                backgroundColor: SEMANTIC_COLORS.transparent,
              },
            },
            incidentsBanner: {
              svg: {
                color: LIGHT_COLORS.ui02,
              },
              before: {
                backgroundColor: SEMANTIC_COLORS.transparent,
              },
            },
            foldable: {
              svg: {
                color: LIGHT_COLORS.ui02,
              },
              before: {
                backgroundColor: SEMANTIC_COLORS.transparent,
              },
            },
          },
          button: {
            default: {
              hover: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
              active: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
            },
            incidentsBanner: {
              hover: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
              active: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
            },
            foldable: {
              hover: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
              active: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
            },
          },
        },
        incidentOperation: {
          operationSpinner: {
            default: {
              borderColor: SEMANTIC_COLORS.white,
            },
            selected: {
              borderColor: SEMANTIC_COLORS.white,
            },
          },
        },
        input: {
          placeholder: {
            color: rgba(SEMANTIC_COLORS.white, 0.7),
          },
          backgroundColor: DARK_COLORS.ui02,
          color: rgba(SEMANTIC_COLORS.white, 0.9),
        },
        instancesBar: {
          wrapper: {
            color: SEMANTIC_COLORS.white,
          },
          greyTextStyle: {
            color: SEMANTIC_COLORS.white,
          },
        },
        modal: {
          modalRoot: {
            backgroundColor: rgba(SEMANTIC_COLORS.black, 0.5),
          },
          modalContent: {
            borderColor: DARK_COLORS.ui06,
          },
          modalHeader: {
            borderColor: DARK_COLORS.ui06,
          },
          crossButton: {
            color: SEMANTIC_COLORS.white,
            active: {
              color: SEMANTIC_COLORS.white,
            },
          },
          modalBody: {
            color: SEMANTIC_COLORS.white,
            backgroundColor: DARK_COLORS.ui01,
          },
          modalFooter: {
            backgroundColor: DARK_COLORS.ui02,
            borderColor: DARK_COLORS.ui06,
          },
          closeButton: {
            color: LIGHT_COLORS.ui02,
          },
        },
        operationItems: {
          iconStyle: {
            color: SEMANTIC_COLORS.white,
          },
          default: {
            background: DARK_COLORS.ui04,
            border: DARK_COLORS.ui06,
          },
          hover: {
            background: DARK_COLORS.button05,
            border: DARK_COLORS.button02,
          },
          active: {
            background: DARK_COLORS.button03,
            border: DARK_COLORS.ui05,
          },
        },
        operations: {
          default: {
            borderColor: SEMANTIC_COLORS.white,
          },
          selected: {
            borderColor: SEMANTIC_COLORS.white,
          },
        },
        panel: {
          panelFooter: {
            borderColor: DARK_COLORS.ui04,
            backgroundColor: DARK_COLORS.ui03,
            color: SEMANTIC_COLORS.white,
          },
          panelHeader: {
            backgroundColor: DARK_COLORS.ui03,
            color: SEMANTIC_COLORS.white,
            borderColor: DARK_COLORS.ui04,
          },
          borderColor: DARK_COLORS.ui04,
          backgroundColor: DARK_COLORS.ui02,
        },
        pill: {
          default: {
            color: SEMANTIC_COLORS.white,
            borderColor: DARK_COLORS.ui06,
            backgroundColor: DARK_COLORS.ui05,
          },
          active: {
            color: SEMANTIC_COLORS.white,
            borderColor: SEMANTIC_COLORS.primaryButton03,
            backgroundColor: SEMANTIC_COLORS.selections,
          },
          disabled: {
            borderColor: DARK_COLORS.ui05,
            backgroundColor: DARK_COLORS.button03,
            color: rgba(SEMANTIC_COLORS.white, 0.5),
          },
          hover: {
            backgroundColor: DARK_COLORS.pillHover,
          },
          count: {
            default: {
              backgroundColor: DARK_COLORS.button02,
            },
            active: {
              backgroundColor: SEMANTIC_COLORS.white,
            },
            hover: {
              backgroundColor: DARK_COLORS.button02,
            },
          },
        },
        select: {
          default: {
            backgroundColor: '#3e3f45',
            color: SEMANTIC_COLORS.white,
          },
          disabled: {
            backgroundColor: rgba('#3e3f45', 0.4),
            borderColor: rgba(DARK_COLORS.ui05, 0.2),
            color: rgba(SEMANTIC_COLORS.white, 0.5),
          },
        },
        skeleton: {
          backgroundColor: SEMANTIC_COLORS.badge02,
        },
        spinner: {
          borderColor: SEMANTIC_COLORS.white,
        },
        spinnerSkeleton: {
          skeleton: {
            backgroundColor: rgba(SEMANTIC_COLORS.black, 0.65),
          },
          skeletonSpinner: {
            borderColor: SEMANTIC_COLORS.white,
          },
        },
        stateIcon: {
          color: SEMANTIC_COLORS.white,
        },
        table: {
          th: {
            color: rgba(SEMANTIC_COLORS.white, 0.8),
            after: {
              backgroundColor: DARK_COLORS.ui04,
            },
          },
          td: {
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
          tr: {
            borderColor: DARK_COLORS.ui04,
            odd: {
              backgroundColor: DARK_COLORS.ui02,
            },
          },
          thead: {
            backgroundColor: DARK_COLORS.ui04,
            tr: {
              backgroundColor: DARK_COLORS.ui03,
            },
          },
        },
        textarea: {
          backgroundColor: DARK_COLORS.ui02,
          color: rgba(SEMANTIC_COLORS.white, 0.9),
          placeholder: {
            color: rgba(SEMANTIC_COLORS.white, 0.7),
          },
        },
      },
    },
    opacity: {
      metricPanel: {
        skeletonBar: 0.2,
      },
      dashboard: {
        tileTitle: 0.9,
        skeleton: {
          block: 0.2,
        },
      },
      incidentsTable: {
        incidentTr: {
          default: 1,
          selected: 1,
        },
      },
      variables: {
        codeLine: {
          before: 0.5,
        },
        variablesTable: {
          tr: 0.7,
        },
      },
      flowNodeInstancesTree: {
        bar: {
          nodeIcon: {
            default: 0.75,
            selected: 0.8,
          },
          nodeName: {
            default: 0.9,
            selected: 0.9,
          },
        },
      },
      columnHeader: {
        label: {
          default: 0.7,
          active: 0.9,
          disabled: 0.5,
        },
        sortIcon: {
          default: 0.6,
          active: 0.9,
          disabled: 0.3,
        },
      },
      progressBar: {
        background: 0.2,
      },
      operationsEntry: {
        iconStyle: 0.9,
      },
      modules: {
        badge: 0.8,
        checkbox: {
          default: 0.7,
          checked: 0.9,
        },
        codeModal: {
          codeEditor: 0.5,
        },
        collapseButton: {
          icons: {
            default: 0.5,
            hover: 0.7,
          },
        },
        copyright: 0.7,
        diagram: {
          popoverOverlay: {
            codeLine: 0.5,
          },
        },
        iconButton: {
          icon: {
            default: {
              svg: 1,
            },
            incidentsBanner: {
              svg: 1,
            },
            foldable: {
              svg: 1,
            },
          },
          button: {
            default: {
              hover: {
                before: 0.25,
                svg: 1,
              },
              active: {
                before: 0.4,
                svg: 1,
              },
            },
            incidentsBanner: {
              hover: {
                before: 0.25,
                svg: 1,
              },
              active: {
                before: 0.4,
                svg: 1,
              },
            },
            foldable: {
              hover: {
                before: 0.25,
                svg: 1,
              },
              active: {
                before: 0.4,
                svg: 1,
              },
            },
          },
        },
        instancesBar: {
          label: 0.9,
          bar: {
            active: 0.9,
          },
        },
        modal: {
          crossButton: {
            default: 0.5,
            hover: 0.7,
          },
          modalBodyText: 0.9,
        },
        pill: {
          default: 1,
          active: 1,
          count: {
            default: 1,
            active: 1,
          },
        },
        skeleton: 0.2,
        stateIcon: {
          completedIcon: 0.46,
          canceledIcon: 0.81,
          aliasIcon: 0.46,
        },
      },
    },
    shadows: {
      panelListItem: {
        hover: `0 0 4px 0 ${SEMANTIC_COLORS.black}`,
        active: `inset 0 0 6px 0 ${rgba(SEMANTIC_COLORS.black, 0.4)}`,
      },
      dashboard: {
        panelStyles: `0 3px 6px 0 ${SEMANTIC_COLORS.black}`,
      },
      filters: {
        resetButtonContainer: `0px -2px 4px 0px ${rgba(
          SEMANTIC_COLORS.black,
          0.1
        )}`,
      },
      modules: {
        button: {
          default: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.35)}`,
          primaryFocus: `0 0 0 1px ${DARK_COLORS.linkHover}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
        },
        checkbox: {
          customCheckbox: {
            before: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.5)}`,
            selection: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.5)}`,
          },
        },
        diagram: {
          popoverOverlay: {
            popOver: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.6)}`,
          },
        },
        dropdown: {
          menu: {
            ul: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.6)}`,
          },
          subMenu: {
            ul: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.6)}`,
          },
        },
        operationItems: {
          ul: `0 1px 1px 0 ${rgba(SEMANTIC_COLORS.black, 0.3)}`,
        },
        select: {
          box: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.35)}`,
          text: `0 0 0 ${SEMANTIC_COLORS.white}`,
        },
        focus: `0 0 0 1px ${DARK_COLORS.focusInner}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
      },
    },
    images: {
      zeebraStripe: darkZeebraStripe,
      incidentsOverlay: incidentsOverlayDarkBackgroundImage,
    },
  },
  light: {
    cmTheme: 'Light',
    colors: {
      ...LIGHT_COLORS,
      metricPanel: {
        skeletonBar: {
          backgroundColor: LIGHT_COLORS.ui06,
        },
      },
      panelListItem: {
        active: {
          borderColor: rgba(LIGHT_COLORS.ui05, 0.4),
        },
        hover: {
          borderColor: rgba(LIGHT_COLORS.ui05, 0.5),
        },
      },
      dashboard: {
        panelStyles: {
          borderColor: LIGHT_COLORS.ui05,
          backgroundColor: LIGHT_COLORS.ui04,
        },
        metricPanelWrapper: {
          color: LIGHT_COLORS.ui06,
        },
        tileTitle: {
          color: LIGHT_COLORS.ui06,
        },
        skeleton: {
          block: {
            backgroundColor: LIGHT_COLORS.ui06,
          },
        },
        message: {
          default: {
            color: rgba(LIGHT_COLORS.ui06, 0.8),
          },
          error: {
            color: rgba(SEMANTIC_COLORS.incidentsAndErrors, 0.9),
          },
          success: {
            color: rgba(SEMANTIC_COLORS.allIsWell, 0.9),
          },
        },
      },
      header: {
        navElements: {
          borderColor: rgba(LIGHT_COLORS.ui06, 0.25),
        },
        user: {
          backgroundColor: rgba(LIGHT_COLORS.ui06, 0.09),
        },
        details: {
          borderColor: rgba(LIGHT_COLORS.ui06, 0.25),
        },
        skeleton: {
          backgroundColor: rgba(LIGHT_COLORS.ui06, 0.09),
        },
        color: LIGHT_COLORS.ui06,
      },
      variablesPanel: {
        color: rgba(LIGHT_COLORS.ui06, 0.8),
      },
      variables: {
        skeleton: {
          row: {
            borderColor: LIGHT_COLORS.ui05,
          },
          borderColor: LIGHT_COLORS.ui05,
          backgroundColor: LIGHT_COLORS.ui04,
        },
        placeholder: {
          color: LIGHT_COLORS.ui06,
        },
        td: {
          color: rgba(LIGHT_COLORS.ui06, 0.9),
        },
        tHead: {
          borderColor: LIGHT_COLORS.ui05,
          backgroundColor: LIGHT_COLORS.ui04,
        },
        editButton: {
          disabled: {
            color: DARK_COLORS.ui05,
          },
        },
        icons: {
          color: DARK_COLORS.ui04,
        },
        codeLine: {
          color: LIGHT_COLORS.ui06,
          before: {
            color: LIGHT_COLORS.ui06,
          },
        },
        linesSeparator: {
          backgroundColor: LIGHT_COLORS.ui05,
        },
        variablesTable: {
          tr: {
            borderColor: LIGHT_COLORS.ui05,
            backgroundColor: '#e7e9ed',
          },
        },
        color: rgba(LIGHT_COLORS.ui06, 0.8),
      },
      bottomPanel: {
        borderColor: LIGHT_COLORS.ui05,
      },
      flowNodeInstanceLog: {
        borderColor: LIGHT_COLORS.ui05,
        color: rgba(LIGHT_COLORS.ui06, 0.9),
      },
      flowNodeInstancesTree: {
        bar: {
          nodeIcon: {
            color: LIGHT_COLORS.ui06,
          },
          container: {
            borderColor: LIGHT_COLORS.ui05,
            selected: {
              borderColor: LIGHT_COLORS.ui05,
            },
          },
          nodeName: {
            borderColor: LIGHT_COLORS.ui05,
            color: LIGHT_COLORS.ui06,
            selected: {
              borderColor: rgba(LIGHT_COLORS.ui06, 0.25),
            },
          },
        },
        foldable: {
          summaryLabel: {
            borderColor: LIGHT_COLORS.ui05,
            backgroundColor: LIGHT_COLORS.ui05,
          },
        },
        timeStampLabel: {
          color: LIGHT_COLORS.ui06,
          backgroundColor: rgba(LIGHT_COLORS.ui04, 0.55),
        },
        connectionDot: {
          color: LIGHT_COLORS.ui05,
        },
        nodeDetails: {
          color: rgba(DARK_COLORS.ui04, 0.9),
          selected: {
            color: rgba(SEMANTIC_COLORS.white, 0.9),
          },
        },
        ul: {
          backgroundColor: LIGHT_COLORS.ui05,
        },
      },
      incidentsFilter: {
        filtersWrapper: {
          backgroundColor: LIGHT_COLORS.ui02,
        },
        label: {
          color: LIGHT_COLORS.ui06,
          backgroundColor: LIGHT_COLORS.ui05,
        },
        moreDropdown: {
          dropdownToggle: {
            borderColor: LIGHT_COLORS.ui03,
            color: DARK_COLORS.ui04,
          },
          item: {
            borderColor: DARK_COLORS.ui04,
          },
        },
        buttonsWrapper: {
          backgroundColor: LIGHT_COLORS.ui05,
        },
      },
      incidentsTable: {
        index: {
          color: LIGHT_COLORS.ui06,
        },
        firstTh: {
          before: {
            backgroundColor: LIGHT_COLORS.ui05,
          },
          after: {
            backgroundColor: LIGHT_COLORS.ui05,
          },
        },
        incidentTr: {
          hover: {
            backgroundColor: LIGHT_COLORS.button05,
          },
        },
        fake: {
          backgroundColor: 'yellow',
          before: {
            borderColor: LIGHT_COLORS.ui05,
            backgroundColor: LIGHT_COLORS.ui02,
          },
        },
      },
      incidentsOverlay: {
        backgroundColor: LIGHT_COLORS.ui04,
      },
      incidentsWrapper: {
        borderColor: LIGHT_COLORS.ui05,
      },
      instanceHeader: {
        backgroundColor: LIGHT_COLORS.ui04,
        borderColor: LIGHT_COLORS.ui05,
      },
      topPanel: {
        pseudoBorder: {
          borderColor: LIGHT_COLORS.ui05,
        },
        pane: {
          backgroundColor: LIGHT_COLORS.ui04,
        },
      },
      instance: {
        section: {
          borderColor: LIGHT_COLORS.ui05,
        },
        flowNodeInstanceLog: {
          borderColor: LIGHT_COLORS.ui05,
          color: rgba(LIGHT_COLORS.ui06, 0.9),
        },
        pseudoBorder: {
          borderColor: LIGHT_COLORS.ui05,
        },
        splitPaneTop: {
          backgroundColor: LIGHT_COLORS.ui04,
        },
      },
      emptyMessage: {
        color: LIGHT_COLORS.ui06,
      },
      columnHeader: {
        color: LIGHT_COLORS.ui06,
      },
      list: {
        selectionStatusIndicator: {
          borderColor: LIGHT_COLORS.ui05,
        },
      },
      createOperationDropdown: {
        dropdownButtonStyles: {
          color: LIGHT_COLORS.ui04,
        },
      },
      paginator: {
        page: {
          color: DARK_COLORS.ui02,
          backgroundColor: LIGHT_COLORS.ui05,
          borderColor: LIGHT_COLORS.ui03,
          active: {
            color: SEMANTIC_COLORS.white,
            backgroundColor: rgba(SEMANTIC_COLORS.selections, 0.9),
            borderColor: '#007dff',
          },
          disabled: {
            backgroundColor: LIGHT_COLORS.button04,
            color: LIGHT_COLORS.ui03,
          },
        },
      },
      operationsEntry: {
        entry: {
          color: rgba(LIGHT_COLORS.ui06, 0.9),
          borderColor: LIGHT_COLORS.ui05,
          isRunning: {
            backgroundColor: LIGHT_COLORS.ui04,
          },
        },
        iconStyle: {
          color: DARK_COLORS.ui02,
        },
      },
      operationsPanel: {
        skeleton: {
          entry: {
            backgroundColor: LIGHT_COLORS.ui04,
            color: rgba(LIGHT_COLORS.ui06, 0.9),
            borderColor: LIGHT_COLORS.ui05,
          },
        },
        operationsList: {
          borderColor: LIGHT_COLORS.ui05,
        },
        emptyMessage: {
          borderColor: LIGHT_COLORS.ui05,
          color: LIGHT_COLORS.ui06,
          backgroundColor: LIGHT_COLORS.ui04,
        },
      },
      disclaimer: {
        container: {
          color: '#7e7e7f',
        },
      },
      login: {
        logo: {
          color: LIGHT_COLORS.ui06,
        },
        loginTitle: {
          color: LIGHT_COLORS.ui06,
        },
      },
      modules: {
        badge: {
          filters: {
            color: DARK_COLORS.ui02,
          },
          default: {
            backgroundColor: DARK_COLORS.ui04,
            color: SEMANTIC_COLORS.white,
          },
        },
        button: {
          secondary: {
            backgroundColor: LIGHT_COLORS.button04,
            borderColor: LIGHT_COLORS.ui03,
            color: rgba(DARK_COLORS.ui04, 0.9),
            hover: {
              backgroundColor: LIGHT_COLORS.button05,
              borderColor: LIGHT_COLORS.ui03,
              color: rgba(DARK_COLORS.ui04, 0.9),
            },
            active: {
              borderColor: LIGHT_COLORS.ui03,
              color: rgba(DARK_COLORS.ui02, 0.9),
            },
            disabled: {
              backgroundColor: LIGHT_COLORS.button04,
              color: rgba(DARK_COLORS.ui04, 0.5),
            },
          },
          primary: {
            color: LIGHT_COLORS.ui04,
            disabled: {
              color: rgba(LIGHT_COLORS.ui04, 0.8),
            },
          },
          main: {
            color: rgba(DARK_COLORS.ui04, 0.9),
            borderColor: LIGHT_COLORS.ui03,
            hover: {
              backgroundColor: LIGHT_COLORS.button01,
              borderColor: LIGHT_COLORS.button02,
            },
            focus: {
              borderColor: LIGHT_COLORS.ui03,
            },
            active: {
              backgroundColor: LIGHT_COLORS.ui03,
              borderColor: LIGHT_COLORS.button03,
            },
            disabled: {
              color: rgba(DARK_COLORS.ui04, 0.5),
              backgroundColor: LIGHT_COLORS.button04,
              borderColor: LIGHT_COLORS.ui03,
            },
          },
        },
        checkbox: {
          label: {
            color: LIGHT_COLORS.ui06,
          },
          customCheckbox: {
            before: {
              borderColor: LIGHT_COLORS.ui03,
              backgroundColor: LIGHT_COLORS.ui01,
              selection: {
                backgroundColor: SEMANTIC_COLORS.selections,
              },
            },
            after: {
              borderColor: LIGHT_COLORS.ui06,
              selection: {
                borderColor: SEMANTIC_COLORS.white,
              },
            },
          },
        },
        codeModal: {
          codeEditor: {
            borderColor: LIGHT_COLORS.ui05,
            backgroundColor: LIGHT_COLORS.ui04,
            pre: {
              color: LIGHT_COLORS.ui06,
              before: {
                color: LIGHT_COLORS.ui06,
              },
            },
          },
        },
        collapsablePanel: {
          collapsable: {
            backgroundColor: LIGHT_COLORS.ui02,
          },
          expandButton: {
            backgroundColor: LIGHT_COLORS.ui02,
            color: LIGHT_COLORS.ui06,
          },
        },
        collapseButton: {
          borderColor: LIGHT_COLORS.ui05,
          icons: {
            color: DARK_COLORS.ui06,
            active: {
              color: DARK_COLORS.ui04,
            },
          },
        },
        copyright: {
          color: LIGHT_COLORS.ui06,
        },
        diagram: {
          popoverOverlay: {
            arrowStyle: {
              before: {
                borderColor: LIGHT_COLORS.ui05,
              },
              after: {
                borderColor: LIGHT_COLORS.ui02,
              },
            },
            popOver: {
              backgroundColor: LIGHT_COLORS.ui02,
              color: LIGHT_COLORS.ui06,
              borderColor: LIGHT_COLORS.ui05,
            },
            codeLine: {
              color: LIGHT_COLORS.ui06,
              before: {
                color: LIGHT_COLORS.ui06,
              },
            },
            linesSeparator: {
              backgroundColor: LIGHT_COLORS.ui05,
            },
          },
          statisticOverlay: {
            statistic: {
              active: {
                backgroundColor: SEMANTIC_COLORS.allIsWell,
                color: SEMANTIC_COLORS.white,
              },
              incidents: {
                backgroundColor: SEMANTIC_COLORS.incidentsAndErrors,
                color: SEMANTIC_COLORS.white,
              },
              completed: {
                backgroundColor: SEMANTIC_COLORS.badge01,
                color: LIGHT_COLORS.ui06,
              },
              canceled: {
                backgroundColor: SEMANTIC_COLORS.badge02,
                color: SEMANTIC_COLORS.white,
              },
            },
          },
          outline: {
            fill: rgba(LIGHT_COLORS.selectedEven, 0.5),
          },
          defaultFillColor: LIGHT_COLORS.ui04,
          defaultStrokeColor: LIGHT_COLORS.ui06,
        },
        dropdown: {
          menu: {
            pointerBody: {
              borderColor: LIGHT_COLORS.ui02,
            },
            pointerShadow: {
              borderColor: LIGHT_COLORS.ui05,
            },
            ul: {
              borderColor: LIGHT_COLORS.ui05,
              backgroundColor: LIGHT_COLORS.ui02,
              color: LIGHT_COLORS.ui06,
            },
            topPointer: {
              borderColor: LIGHT_COLORS.ui05,
            },
            bottomPointer: {
              borderColor: LIGHT_COLORS.ui05,
            },
            li: {
              borderColor: LIGHT_COLORS.ui05,
            },
          },
          option: {
            borderColor: LIGHT_COLORS.ui05,
            optionButton: {
              disabled: {
                color: rgba(LIGHT_COLORS.ui06, 0.6),
              },
              default: {
                color: rgba(LIGHT_COLORS.ui06, 0.9),
              },
              hover: {
                backgroundColor: LIGHT_COLORS.ui05,
              },
            },
          },
          subMenu: {
            subMenuButton: {
              color: rgba(LIGHT_COLORS.ui06, 0.9),
              hover: {
                backgroundColor: LIGHT_COLORS.ui05,
              },
            },
            ul: {
              backgroundColor: LIGHT_COLORS.ui02,
              color: LIGHT_COLORS.ui06,
              borderColor: LIGHT_COLORS.ui05,
            },
            li: {
              borderColor: LIGHT_COLORS.ui05,
            },
          },
          subOption: {
            optionButton: {
              color: rgba(LIGHT_COLORS.ui06, 0.9),
              hover: {
                backgroundColor: LIGHT_COLORS.ui05,
              },
            },
          },
          button: {
            default: {
              color: rgba(LIGHT_COLORS.ui06, 0.9),
            },
            disabled: {
              color: rgba(LIGHT_COLORS.ui06, 0.6),
            },
          },
        },
        emptyPanel: {
          color: LIGHT_COLORS.ui06,
        },
        flowNodeIcon: {
          color: LIGHT_COLORS.ui06,
        },
        iconButton: {
          icon: {
            default: {
              svg: {
                color: DARK_COLORS.ui04,
              },
              before: {
                backgroundColor: SEMANTIC_COLORS.transparent,
              },
            },
            incidentsBanner: {
              svg: {
                color: LIGHT_COLORS.ui02,
              },
              before: {
                backgroundColor: SEMANTIC_COLORS.transparent,
              },
            },
            foldable: {
              svg: {
                color: DARK_COLORS.ui04,
              },
              before: {
                backgroundColor: SEMANTIC_COLORS.transparent,
              },
            },
          },
          button: {
            default: {
              hover: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
              active: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
            },
            incidentsBanner: {
              hover: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
              active: {
                before: {
                  backgroundColor: SEMANTIC_COLORS.white,
                },
                svg: {
                  color: LIGHT_COLORS.ui02,
                },
              },
            },
            foldable: {
              hover: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
              active: {
                before: {
                  backgroundColor: LIGHT_COLORS.ui05,
                },
                svg: {
                  color: DARK_COLORS.ui04,
                },
              },
            },
          },
        },
        incidentOperation: {
          operationSpinner: {
            default: {
              borderColor: LIGHT_COLORS.ui06,
            },
            selected: {
              borderColor: SEMANTIC_COLORS.selections,
            },
          },
        },
        input: {
          placeholder: {
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
          backgroundColor: LIGHT_COLORS.ui04,
          color: rgba(DARK_COLORS.ui03, 0.9),
        },
        instancesBar: {
          wrapper: {
            color: LIGHT_COLORS.ui06,
          },
          greyTextStyle: {
            color: SEMANTIC_COLORS.badge02,
          },
        },
        modal: {
          modalRoot: {
            backgroundColor: rgba(SEMANTIC_COLORS.white, 0.7),
          },
          modalContent: {
            borderColor: LIGHT_COLORS.ui05,
          },
          modalHeader: {
            borderColor: LIGHT_COLORS.ui05,
          },
          crossButton: {
            color: LIGHT_COLORS.ui06,
            active: {
              color: DARK_COLORS.ui04,
            },
          },
          modalBody: {
            color: LIGHT_COLORS.ui06,
            backgroundColor: LIGHT_COLORS.ui04,
          },
          modalFooter: {
            backgroundColor: LIGHT_COLORS.ui04,
            borderColor: LIGHT_COLORS.ui05,
          },
          closeButton: {
            color: LIGHT_COLORS.ui02,
          },
        },
        operationItems: {
          iconStyle: {
            color: DARK_COLORS.ui02,
          },
          default: {
            background: LIGHT_COLORS.button04,
            border: LIGHT_COLORS.ui03,
          },
          hover: {
            background: LIGHT_COLORS.button06,
            border: LIGHT_COLORS.button02,
          },
          active: {
            background: LIGHT_COLORS.ui03,
            border: LIGHT_COLORS.button03,
          },
        },
        operations: {
          default: {
            borderColor: LIGHT_COLORS.ui06,
          },
          selected: {
            borderColor: SEMANTIC_COLORS.selections,
          },
        },
        panel: {
          panelFooter: {
            borderColor: LIGHT_COLORS.ui05,
            backgroundColor: LIGHT_COLORS.ui02,
            color: LIGHT_COLORS.ui06,
          },
          panelHeader: {
            backgroundColor: LIGHT_COLORS.ui02,
            color: LIGHT_COLORS.ui06,
            borderColor: LIGHT_COLORS.ui05,
          },
          borderColor: LIGHT_COLORS.ui05,
          backgroundColor: LIGHT_COLORS.ui04,
        },
        pill: {
          default: {
            color: DARK_COLORS.ui05,
            borderColor: LIGHT_COLORS.ui03,
            backgroundColor: LIGHT_COLORS.ui05,
          },
          active: {
            color: SEMANTIC_COLORS.white,
            borderColor: SEMANTIC_COLORS.primaryButton03,
            backgroundColor: SEMANTIC_COLORS.selections,
          },
          disabled: {
            borderColor: LIGHT_COLORS.ui03,
            backgroundColor: LIGHT_COLORS.button04,
            color: rgba(DARK_COLORS.ui04, 0.5),
          },
          hover: {
            backgroundColor: LIGHT_COLORS.button01,
          },
          count: {
            default: {
              backgroundColor: LIGHT_COLORS.ui06,
            },
            active: {
              backgroundColor: SEMANTIC_COLORS.white,
            },
            hover: {
              backgroundColor: LIGHT_COLORS.ui06,
            },
          },
        },
        select: {
          default: {
            backgroundColor: LIGHT_COLORS.ui01,
            color: DARK_COLORS.ui06,
          },
          disabled: {
            backgroundColor: rgba(LIGHT_COLORS.ui01, 0.4),
            borderColor: rgba(LIGHT_COLORS.ui03, 0.2),
            color: rgba(LIGHT_COLORS.ui06, 0.7),
          },
        },
        skeleton: {
          backgroundColor: LIGHT_COLORS.ui06,
        },
        spinner: {
          borderColor: SEMANTIC_COLORS.badge02,
        },
        spinnerSkeleton: {
          skeleton: {
            backgroundColor: rgba(SEMANTIC_COLORS.white, 0.75),
          },
          skeletonSpinner: {
            borderColor: LIGHT_COLORS.ui06,
          },
        },
        stateIcon: {
          color: LIGHT_COLORS.ui06,
        },
        table: {
          th: {
            color: rgba(LIGHT_COLORS.ui06, 0.8),
            after: {
              backgroundColor: LIGHT_COLORS.ui05,
            },
          },
          td: {
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
          tr: {
            borderColor: LIGHT_COLORS.ui05,
            odd: {
              backgroundColor: LIGHT_COLORS.ui04,
            },
          },
          thead: {
            backgroundColor: LIGHT_COLORS.ui02,
            tr: {
              backgroundColor: LIGHT_COLORS.ui02,
            },
          },
        },
        textarea: {
          backgroundColor: LIGHT_COLORS.ui04,
          color: DARK_COLORS.ui03,
          placeholder: {
            color: rgba(LIGHT_COLORS.ui06, 0.9),
          },
        },
      },
    },
    opacity: {
      metricPanel: {
        skeletonBar: 0.09,
      },
      dashboard: {
        tileTitle: 1,
        skeleton: {
          block: 0.09,
        },
      },
      incidentsTable: {
        incidentTr: {
          default: 0.9,
          selected: 1,
        },
      },
      variables: {
        codeLine: {
          before: 0.65,
        },
        variablesTable: {
          tr: 1,
        },
      },
      flowNodeInstancesTree: {
        bar: {
          nodeIcon: {
            default: 0.6,
            selected: 0.65,
          },
          nodeName: {
            default: 0.9,
            selected: 1,
          },
        },
      },
      columnHeader: {
        label: {
          default: 0.8,
          active: 1,
          disabled: 0.6,
        },
        sortIcon: {
          default: 0.6,
          active: 1,
          disabled: 0.3,
        },
      },
      progressBar: {
        background: 0.3,
      },
      operationsEntry: {
        iconStyle: 0.8,
      },
      modules: {
        badge: 0.7,
        checkbox: {
          default: 0.7,
          checked: 1,
        },
        codeModal: {
          codeEditor: 0.65,
        },
        collapseButton: {
          icons: {
            default: 0.9,
            hover: 1,
          },
        },
        copyright: 0.9,
        diagram: {
          popoverOverlay: {
            codeLine: 0.65,
          },
        },
        iconButton: {
          icon: {
            default: {
              svg: 0.9,
            },
            incidentsBanner: {
              svg: 1,
            },
            foldable: {
              svg: 0.9,
            },
          },
          button: {
            default: {
              hover: {
                svg: 0.9,
                before: 0.5,
              },
              active: {
                svg: 1,
                before: 0.8,
              },
            },
            incidentsBanner: {
              hover: {
                before: 0.25,
                svg: 1,
              },
              active: {
                before: 0.4,
                svg: 1,
              },
            },
            foldable: {
              hover: {
                before: 0.5,
                svg: 0.9,
              },
              active: {
                before: 0.8,
                svg: 1,
              },
            },
          },
        },
        instancesBar: {
          label: 1,
          bar: {
            active: 0.4,
          },
        },
        modal: {
          crossButton: {
            default: 0.9,
            hover: 1,
          },
          modalBodyText: 1,
        },
        pill: {
          default: 0.5,
          active: 1,
          count: {
            default: 0.55,
            active: 1,
          },
        },
        skeleton: 0.09,
        stateIcon: {
          completedIcon: 0.4,
          canceledIcon: 0.75,
          aliasIcon: 0.4,
        },
      },
    },
    shadows: {
      panelListItem: {
        hover: `0 0 5px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        active: `inset 0 0 6px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
      },
      dashboard: {
        panelStyles: `0 2px 3px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
      },
      filters: {
        resetButtonContainer: `0px -1px 2px 0px ${rgba(
          SEMANTIC_COLORS.black,
          0.1
        )}`,
      },
      modules: {
        button: {
          default: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.08)}`,
          primaryFocus: `0 0 0 1px ${DARK_COLORS.linkHover}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
        },
        checkbox: {
          customCheckbox: {
            before: `0 2px 2px 0 ${rgba(LIGHT_COLORS.button05, 0.35)}`,
            selection: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.5)}`,
          },
        },
        diagram: {
          popoverOverlay: {
            popOver: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
          },
        },
        dropdown: {
          menu: {
            ul: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
          },
          subMenu: {
            ul: `0 0 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.2)}`,
          },
        },
        operationItems: {
          ul: `0 1px 1px 0 ${rgba(SEMANTIC_COLORS.black, 0.1)}`,
        },
        select: {
          box: `0 2px 2px 0 ${rgba(SEMANTIC_COLORS.black, 0.08)}`,
          text: `0 0 0 ${DARK_COLORS.ui06}`,
        },
        focus: `0 0 0 1px ${DARK_COLORS.focusInner}, 0 0 0 4px ${SEMANTIC_COLORS.focusOuter}`,
      },
    },
    images: {
      zeebraStripe: lightZeebraStripe,
      incidentsOverlay: incidentsOverlayLightBackgroundImage,
    },
  },
});

export {theme};
