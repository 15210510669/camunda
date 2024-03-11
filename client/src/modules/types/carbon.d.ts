/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type PolymorphicRef<C extends React.ElementType> =
  React.ComponentPropsWithRef<C>['ref'];

type AsProp<C extends React.ElementType> = {
  as?: C;
};

type PropsToOmit<C extends React.ElementType, P> = keyof (AsProp<C> & P);

type PolymorphicComponentProp<
  C extends React.ElementType,
  Props = object,
> = React.PropsWithChildren<Props & AsProp<C>> &
  Omit<React.ComponentPropsWithoutRef<C>, PropsToOmit<C, Props>>;

type PolymorphicComponentPropWithRef<
  C extends React.ElementType,
  Props = object,
> = PolymorphicComponentProp<C, Props> & {ref?: PolymorphicRef<C>};

type Size = 'sm' | 'md' | 'lg' | 'xl';

declare module '@carbon/react' {
  type ThemeType = 'white' | 'g10' | 'g90' | 'g100';

  export const Theme: React.FunctionComponent<{
    children: React.ReactNode;
    theme?: ThemeType;
    className?: string;
  }>;

  export const GlobalTheme: React.FunctionComponent<{
    children: React.ReactNode;
    theme?: ThemeType;
    className?: string;
  }>;

  type StackProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        gap?: 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13;
        orientation?: 'horizontal' | 'vertical';
      }
    >;

  export const Stack: <C extends React.ElementType = 'div'>(
    props: StackProps<C>,
  ) => React.ReactElement | null;

  export const IconButton: React.FunctionComponent<
    {
      align?:
        | 'top'
        | 'top-left'
        | 'top-right'
        | 'bottom'
        | 'bottom-left'
        | 'bottom-right'
        | 'left'
        | 'right';
      defaultOpen?: boolean;
      disabled?: boolean;
      enterDelayMs?: number;
      kind?: 'primary' | 'secondary' | 'ghost' | 'tertiary';
      label: string;
      leaveDelayMs?: number;
      size?: 'sm' | 'md' | 'lg';
      className?: string;
      onClick?: () => void;
      children?: React.ReactNode;
    } & React.ButtonHTMLAttributes<HTMLButtonElement>
  >;

  export const Header: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
    'aria-label'?: string;
    'aria-labelledby'?: string;
  }>;

  export const Toggletip: React.FunctionComponent<{
    align?: 'top' | 'bottom' | 'left' | 'right';
    as?: keyof JSX.IntrinsicElements | React.ReactElement;
    children?: React.ReactNode;
    className?: string;
    defaultOpen?: boolean;
  }>;

  export const ToggletipButton: React.FunctionComponent<{
    children: React.ReactNode;
    className?: string;
    label?: string;
  }>;

  export const ToggletipContent: React.FunctionComponent<{
    children: React.ReactNode;
    className?: string;
  }>;

  export const SwitcherItem: React.FunctionComponent<
    {
      children: React.ReactNode;
    } & React.AnchorHTMLAttributes<HTMLAnchorElement>
  >;

  type LayerProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        children?: React.ReactNode;
        className?: string;
        level?: 0 | 1 | 2;
      }
    >;

  export const Layer: <C extends React.ElementType = 'div'>(
    props: StackProps<C>,
  ) => React.ReactElement | null;

  export const ActionableNotification: React.FunctionComponent<{
    actionButtonLabel: string;
    'aria-label'?: string;
    caption?: string;
    children?: React.ReactNode;
    className?: string;
    closeOnEscape?: boolean;
    hasFocus?: boolean;
    hideCloseButton?: boolean;
    inline?: boolean;
    kind:
      | 'error'
      | 'info'
      | 'info-square'
      | 'success'
      | 'warning'
      | 'warning-alt';
    lowContrast?: boolean;
    onActionButtonClick?: () => void;
    onClose?: () => void;
    onCloseButtonClick?: () => void;
    role?: string;
    statusIconDescription?: string;
    subtitle?: string;
    title?: string;
    style?: Partial<CSSStyleDeclaration>;
  }>;

  export const ContainedList: React.FunctionComponent<{
    action?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    isInset?: boolean;
    kind?: 'on-page' | 'disclosed';
    label: string | React.ReactNode;
    size?: Size;
  }>;

  export const ContainedListItem: React.FunctionComponent<{
    action?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    disabled?: boolean;
    onClick?: () => void;
    renderIcon?: (() => void) | object;
  }>;

  type SectionProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        children?: React.ReactNode;
        className?: string;
        level?: 1 | 2 | 3 | 4 | 5 | 6;
      }
    >;

  export const Section: <C extends React.ElementType = 'section'>(
    props: SectionProps<C>,
  ) => React.ReactElement | null;

  export const Heading: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
  }>;

  export const Toggle: React.FC<{
    'aria-labelledby'?: string;
    id: string;
    labelA?: string;
    labelB?: string;
    labelText?: string;
    hideLabel?: boolean;
    onClick?:
      | React.MouseEventHandler<HTMLDivElement>
      | React.KeyboardEventHandler<HTMLDivElement>;
    onToggle?(checked: boolean): void;
    size?: 'sm' | 'md';
    readOnly?: boolean;
    defaultToggled?: boolean;
    toggled?: boolean;
  }>;

  export const Copy: React.FunctionComponent<{
    children?: React.ReactNode;
    className?: string;
    align?:
      | 'top'
      | 'top-left'
      | 'top-right'
      | 'bottom'
      | 'bottom-left'
      | 'bottom-right'
      | 'left'
      | 'right';
    feedback?: string;
    feedbackTimeout?: number;
    onAnimationEnd?: () => void;
    onClick?: () => void;
  }>;

  type FlexGridProps<C extends React.ElementType> =
    PolymorphicComponentPropWithRef<
      C,
      {
        className?: string;
        condensed?: boolean;
        fullWidth?: boolean;
        narrow?: boolean;
      }
    >;

  export const FlexGrid: <C extends React.ElementType = 'div'>(
    props: FlexGridProps<C>,
  ) => React.ReactElement | null;

  export const useTheme: () => {theme: ThemeType};

  export const usePrefix: () => string;

  export const OverflowMenu: React.FunctionComponent<{
    'aria-label'?: string;
    children?: React.ReactNode;
    className?: string;
    direction?: 'top' | 'bottom';
    flipped?: boolean;
    focusTrap?: boolean;
    iconClass?: string;
    iconDescription?: string;
    id?: string;
    menuOffset?: {top: number; left: number} | (() => void);
    menuOffsetFlip?: {top: number; left: number} | (() => void);
    menuOptionsClass?: string;
    onClick?: React.MouseEventHandler<HTMLDivElement>;
    onClose?: () => void;
    onFocus?: React.FocusEventHandler<HTMLDivElement>;
    onKeyDown?: React.KeyboardEventHandler<HTMLDivElement>;
    onBlur?: React.FocusEventHandler<HTMLDivElement>;
    disabled?: boolean;
    onOpen?: () => void;
    open?: boolean;
    renderIcon?: React.FC | object;
    selectorPrimaryFocus?: string;
    size?: 'sm' | 'md' | 'lg';
    align?:
      | 'top'
      | 'top-left'
      | 'top-right'
      | 'bottom'
      | 'bottom-left'
      | 'bottom-right'
      | 'left'
      | 'right';
  }>;

  export * from 'carbon-components-react';
}

declare module '@carbon/react/icons' {
  type Icon = React.FunctionComponent<
    {
      className?: string;
      alt?: string;
      'aria-label'?: string;
      size?: 16 | 20 | 24 | 32;
    } & React.HTMLAttributes<HTMLOrSVGElement>
  >;

  export const ArrowRight: Icon;
  export const Devices: Icon;
  export const Moon: Icon;
  export const Light: Icon;
  export const InformationFilled: Icon;
  export const Information: Icon;
  export const Search: Icon;
  export const Close: Icon;
  export const Popup: Icon;
  export const Add: Icon;
  export const RowCollapse: Icon;
  export const SortAscending: Icon;
  export const Checkmark: Icon;
  export const Error: Icon;
  export const Warning: Icon;
  export const WarningFilled: Icon;
  export const CheckmarkOutline: Icon;
  export const RadioButtonChecked: Icon;
  export const Share: Icon;
  export const Filter: Icon;
}

declare module '@carbon/elements' {
  import {
    white,
    whiteHover,
    gray10,
    gray10Hover,
    gray20,
    gray20Hover,
    gray30,
    gray60,
    gray70,
    gray70Hover,
    gray80,
    gray80Hover,
    gray90Hover,
    styles as originalStyles,
    g10 as originalG10,
    g100 as originalG100,
  } from '@types/carbon__elements';

  const borderSubtle00: typeof gray10 | typeof gray80;
  const borderSubtle01: typeof gray20 | typeof gray80;
  const layer01: typeof gray70 | typeof white;
  const layerActive01: typeof gray30 | typeof gray70;
  const borderSubtleSelected01: typeof gray30 | typeof gray70;
  const layerHover01: typeof whiteHover | typeof gray90Hover;
  const layerSelected01: typeof gray20 | typeof gray80;
  const layerSelectedHover01: typeof gray20Hover | typeof gray80Hover;
  const layer02: typeof gray10 | typeof gray80;
  const layerActive02: typeof gray30 | typeof gray60;
  const layerHover02: typeof gray10Hover | typeof gray80Hover;
  const layerSelected02: typeof gray20 | typeof gray70;
  const layerSelectedHover02: typeof gray20Hover | typeof gray70Hover;
  const g10 = {
    ...originalG10,
    borderSubtle00,
    borderSubtle01,
    layer01,
    layerActive01,
    borderSubtleSelected01,
    layerHover01,
    layerSelected01,
    layerSelectedHover01,
    layer02,
    layerActive02,
    layerHover02,
    layerSelected02,
    layerSelectedHover02,
  } as const;
  const g100 = {
    ...originalG100,
    borderSubtle00,
    borderSubtle01,
    layer01,
    layerActive01,
    borderSubtleSelected01,
    layerHover01,
    layerSelected01,
    layerSelectedHover01,
    layer02,
    layerActive02,
    layerHover02,
    layerSelected02,
    layerSelectedHover02,
  } as const;
  const styles = {
    ...originalStyles,
    legal01: {
      fontSize: '0.75rem',
      fontWeight: 400,
      lineHeight: 1.33333,
      letterSpacing: '0.32px',
    },
    legal02: {
      fontSize: '0.875rem',
      fontWeight: 400,
      lineHeight: 1.28572,
      letterSpacing: '0.16px',
    },
  } as const;
  export * from '@types/carbon__elements';

  export {borderSubtle00, borderSubtle01, styles, g10, g100};
}
