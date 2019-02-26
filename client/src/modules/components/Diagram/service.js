import {Colors, themeStyle} from 'modules/theme';
import {POPOVER_SIDE} from 'modules/constants';

const POPOVER_TO_FLOWNODE_SPACE = 16;

export function getDiagramColors(theme) {
  return {
    defaultFillColor: themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04
    })({theme}),
    defaultStrokeColor: themeStyle({
      dark: Colors.darkDiagram,
      light: Colors.uiLight06
    })({theme})
  };
}

export function getPopoverPostion(
  {diagramContainer, flowNode},
  isSummaryPopover
) {
  // we only know the popover dimensions after it's render, so we approximate
  const POPOVER_APROXIMATE_HEIGHT = isSummaryPopover
    ? POPOVER_TO_FLOWNODE_SPACE + 54 // 80
    : POPOVER_TO_FLOWNODE_SPACE + 104; // 120
  const POPOVER_APROXIMATE_WIDTH = 190;

  const containerBoundary = diagramContainer.getBoundingClientRect();
  const flowNodeBoundary = flowNode.getBoundingClientRect();
  const flowNodeBBox = flowNode.getBBox();

  // space between the bottom of the flow node and the end of the diagram container
  const spaceToBottom = containerBoundary.bottom - flowNodeBoundary.bottom;
  // space between the left of the flow node and the end of the diagram container
  const spaceToLeft = flowNodeBoundary.left - containerBoundary.left;
  // space between the top of the flow node and the end of the diagram container
  const spaceToTop = flowNodeBoundary.top - containerBoundary.top;
  // space between the right of the flow node and the end of the diagram container
  const spaceToRight = containerBoundary.right - flowNodeBoundary.right;

  // space to the left of the popover, if it gets position at the bottom/top of the flow node
  const verticalPopoverSpaceToLeft =
    flowNodeBoundary.left + flowNodeBoundary.width / 2;

  // space to the right of the popover, if it gets position at the bottom/top of the flow node
  const verticalPopoverSpaceToRight =
    flowNodeBoundary.right + flowNodeBoundary.width / 2;

  // space to the bottom of the popover, if it gets position at the left/right of the flow node
  const horizontalPopoverSpaceToBottom =
    containerBoundary.bottom -
    flowNodeBoundary.bottom +
    flowNodeBoundary.height / 2;

  // space to the top of the popover, if it gets position at the left/right of the flow node
  const horizontalPopoverSpaceToTOP =
    flowNodeBoundary.top + flowNodeBoundary.height / 2;

  // can the popover be positioned at the bottom of the flow node?
  if (
    spaceToBottom > POPOVER_APROXIMATE_HEIGHT &&
    verticalPopoverSpaceToLeft > POPOVER_APROXIMATE_WIDTH / 2 &&
    verticalPopoverSpaceToRight > POPOVER_APROXIMATE_WIDTH / 2
  ) {
    return {
      bottom: -POPOVER_TO_FLOWNODE_SPACE,
      left: flowNodeBBox.width / 2,
      side: POPOVER_SIDE.BOTTOM
    };
  }

  // can the popover be positioned at the left of the flow node?
  if (
    spaceToLeft > POPOVER_APROXIMATE_WIDTH &&
    horizontalPopoverSpaceToBottom > POPOVER_APROXIMATE_HEIGHT / 2 &&
    horizontalPopoverSpaceToTOP > POPOVER_APROXIMATE_HEIGHT / 2
  ) {
    return {
      left: -POPOVER_TO_FLOWNODE_SPACE,
      top: flowNodeBBox.height / 2,
      side: POPOVER_SIDE.LEFT
    };
  }

  // can the popover be positioned at the top of the flow node?
  if (
    spaceToTop > POPOVER_APROXIMATE_HEIGHT &&
    verticalPopoverSpaceToLeft > POPOVER_APROXIMATE_WIDTH / 2 &&
    verticalPopoverSpaceToRight > POPOVER_APROXIMATE_WIDTH / 2
  ) {
    return {
      top: -POPOVER_TO_FLOWNODE_SPACE,
      left: flowNodeBBox.width / 2,
      side: POPOVER_SIDE.TOP
    };
  }

  // can the popover be positioned at the right of the flow node?
  if (
    spaceToRight > POPOVER_APROXIMATE_WIDTH &&
    horizontalPopoverSpaceToBottom > POPOVER_APROXIMATE_HEIGHT &&
    horizontalPopoverSpaceToTOP > POPOVER_APROXIMATE_HEIGHT
  ) {
    return {
      top: flowNodeBBox.height / 2,
      right: -POPOVER_TO_FLOWNODE_SPACE,
      side: POPOVER_SIDE.RIGHT
    };
  }

  // position the popover in a mirrored position (from bottom to top) at the bottom of the flow node
  return {
    bottom: POPOVER_TO_FLOWNODE_SPACE,
    left: flowNodeBBox.width / 2,
    side: POPOVER_SIDE.BOTTOM_MIRROR
  };
}
