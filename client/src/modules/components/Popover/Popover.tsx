/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode, useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react';
import {Button, Icon, Tooltip, TooltipProps} from 'components';
import {getScreenBounds} from 'services';
import {
  Popover as CarbonPopover,
  PopoverAlignment,
  PopoverContent,
  PopoverProps as CarbonPopoverProps,
} from '@carbon/react';

import classNames from 'classnames';

import './Popover.scss';

const possibleAlignments: PopoverAlignment[] = [
  'top',
  'top-left',
  'top-right',
  'bottom',
  'bottom-left',
  'bottom-right',
  'left',
  'left-bottom',
  'left-top',
  'right',
  'right-bottom',
  'right-top',
];

interface PopoverProps extends Omit<CarbonPopoverProps<'div'>, 'title' | 'open' | 'align'> {
  className?: string;
  children: ReactNode;
  title?: ReactNode;
  main?: boolean;
  disabled?: boolean;
  icon?: string;
  floating?: boolean;
  onOpen?: () => void;
  onClose?: () => void;
  tooltip?: TooltipProps['content'];
  autoOpen?: boolean;
  align?: PopoverAlignment;
  tooltipPosition?: TooltipProps['position'];
}

export default function Popover({
  className,
  children,
  title,
  main,
  disabled,
  icon,
  floating,
  onOpen,
  onClose,
  tooltip,
  autoOpen = false,
  align,
  tooltipPosition,
  ...props
}: PopoverProps): JSX.Element {
  const [open, setOpen] = useState(autoOpen);
  const [scrollable, setScrollable] = useState<boolean>(false);
  const [popoverStyles, setPopoverStyles] = useState({});
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const transformParent = useRef<HTMLElement | null>(null);
  const isInsideClick = useRef<boolean>(false);

  const calculateDialogStyle = useCallback(() => {
    const popoverClassList = popoverRef.current?.classList;
    if (!popoverClassList || !buttonRef.current || !dialogRef.current || !contentRef.current) {
      return;
    }

    const dialogStyles = dialogRef.current.style;

    popoverClassList.remove(...possibleAlignments.map(getClassName));
    dialogRef.current.removeAttribute('style');

    const overlayWidth = dialogRef.current.clientWidth;
    const overlayHeight = dialogRef.current.clientHeight;
    const contentHeight = contentRef.current.clientHeight;
    const buttonRect = buttonRef.current.getBoundingClientRect();
    const buttonCenter = buttonRect.left + buttonRect.width / 2;

    const bounds = getScrollBounds(transformParent.current);

    const bodyWidth = document.body.clientWidth;
    const margin = 10;
    const padding = 10 + 15;

    let newAlignment: PopoverAlignment = 'bottom';

    if (buttonCenter + overlayWidth / 2 > bodyWidth) {
      newAlignment = 'bottom-right';
    }

    if (buttonCenter - overlayWidth / 2 < 0) {
      newAlignment = 'bottom-left';
    }

    if (
      overlayHeight + buttonRect.bottom > bounds.bottom - margin ||
      contentHeight > overlayHeight
    ) {
      dialogStyles.height = bounds.bottom - buttonRect.bottom - 2 * margin + 'px';
      setScrollable(true);
    }

    const topSpace = buttonRect.bottom - bounds.top - margin;
    const bottomSpace = bounds.bottom - buttonRect.bottom - margin;
    const contentHeightWithPadding = contentHeight + padding;

    if (bottomSpace < contentHeightWithPadding && topSpace > bottomSpace) {
      const scrollable = contentHeightWithPadding > topSpace;
      setScrollable(scrollable);
      dialogStyles.height = (scrollable ? topSpace : contentHeightWithPadding) + 'px';
      newAlignment = newAlignment.replace('bottom', 'top') as PopoverAlignment;
    }

    popoverClassList.add(getClassName(align || newAlignment));
  }, [align]);

  const fixPositioning = useCallback(() => {
    if (!floating) {
      return;
    }

    const {top, left} = transformParent.current?.getBoundingClientRect() || {
      top: 0,
      left: 0,
    };
    const box = buttonRef.current?.getBoundingClientRect();

    if (open && box) {
      setPopoverStyles({
        position: 'fixed',
        left: box.left - left + 'px',
        top: box.top - top + 'px',
        width: box.width,
        height: box.height,
      });
    }
  }, [floating, open]);

  useEffect(() => {
    if (open) {
      onOpen?.();
    } else if (!open) {
      onClose?.();
    }
  }, [onClose, onOpen, open]);

  const handleResize = useCallback(() => {
    calculateDialogStyle();
    fixPositioning();
  }, [calculateDialogStyle, fixPositioning]);

  useLayoutEffect(() => {
    const observer = new MutationObserver(handleResize);

    if (open) {
      if (floating) {
        transformParent.current = getClosestElementByStyle(
          popoverRef.current,
          (style) => style.transform !== 'none'
        );
      }
      handleResize();
      window.addEventListener('resize', handleResize);
      if (dialogRef.current) {
        observer.observe(dialogRef.current, {
          childList: true,
          subtree: true,
        });
      }
    } else {
      window.removeEventListener('resize', handleResize);
      observer.disconnect();
    }

    return () => {
      window.removeEventListener('resize', handleResize);
      observer.disconnect();
    };
  }, [handleResize, open, floating]);

  const handleOutsideClick = (evt: Event) => {
    if (
      popoverRef.current &&
      evt.target instanceof Element &&
      !popoverRef.current.contains(evt.target) &&
      !isInsideClick.current
    ) {
      setOpen(false);
    }

    isInsideClick.current = false;
  };

  useEffect(() => {
    if (open) {
      document.addEventListener('click', handleOutsideClick, {capture: true});
    } else {
      document.removeEventListener('click', handleOutsideClick, {capture: true});
    }

    return () => {
      document.removeEventListener('click', handleOutsideClick, {capture: true});
    };
  }, [open]);

  return (
    <CarbonPopover
      className={classNames(className, 'Popover')}
      {...props}
      open={open}
      ref={popoverRef}
    >
      <Tooltip content={tooltip} position={tooltipPosition}>
        <div className="buttonWrapper">
          <Button
            onClick={() => setOpen(!open)}
            active={!disabled && open}
            main={main}
            disabled={disabled}
            icon={!!icon && !title}
            ref={buttonRef}
          >
            {icon ? <Icon type={icon} /> : ''}
            {title}
            <Icon type="down" className="downIcon" />
          </Button>
        </div>
      </Tooltip>
      {open && (
        <PopoverContent
          className={classNames('popoverContent', {scrollable})}
          ref={dialogRef}
          style={popoverStyles}
          onMouseDownCapture={() => {
            isInsideClick.current = true;
          }}
        >
          <div ref={contentRef}>{children}</div>
        </PopoverContent>
      )}
    </CarbonPopover>
  );
}

function getClassName(alignment: PopoverAlignment) {
  return 'cds--popover--' + alignment;
}

function getScrollBounds(element: HTMLElement | null) {
  if (!element) {
    return getScreenBounds();
  }

  const scrollParent = getClosestElementByStyle(element, (style) => style.overflow !== 'visible');

  return scrollParent?.getBoundingClientRect() || getScreenBounds();
}

function getClosestElementByStyle(
  element: HTMLElement | null,
  check: (style: CSSStyleDeclaration) => boolean
) {
  let currentNode = element;
  while (currentNode) {
    const computedStyle = window.getComputedStyle(currentNode);
    if (check(computedStyle)) {
      return currentNode;
    }
    currentNode = currentNode.parentElement;
  }

  return null;
}
