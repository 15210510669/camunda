/* eslint import/no-webpack-loader-syntax: 0 */

import autorefresh from '-!svg-react-loader!./autorefresh.svg';
import check from '-!svg-react-loader!./check.svg';
import deleteIcon from '-!svg-react-loader!./delete.svg';
import exitFullscreen from '-!svg-react-loader!./exit-fullscreen.svg';
import fullscreen from '-!svg-react-loader!./fullscreen.svg';
import plus from '-!svg-react-loader!./plus.svg';
import share from '-!svg-react-loader!./share.svg';
import edit from '-!svg-react-loader!./edit.svg';
import close from '-!svg-react-loader!./close.svg';
import stop from '-!svg-react-loader!./stop.svg';
import link from '-!svg-react-loader!./link.svg';
import embed from '-!svg-react-loader!./embed.svg';
import save from '-!svg-react-loader!./save.svg';
import overflowMenuVertical from '-!svg-react-loader!./overflow-menu-vertical.svg';
import overflowMenuHorizontal from '-!svg-react-loader!./overflow-menu-horizontal.svg';
import openInCockpit from '-!svg-react-loader!./open-in-cockpit.svg';
import copyDocument from '-!svg-react-loader!./copy-document.svg';

const icons = {
  autorefresh,
  check,
  delete: deleteIcon,
  'exit-fullscreen': exitFullscreen,
  fullscreen,
  plus,
  share,
  edit,
  close,
  stop,
  link,
  embed,
  save,
  'overflow-menu-vertical': overflowMenuVertical,
  'overflow-menu-horizontal': overflowMenuHorizontal,
  'open-in-cockpit': openInCockpit,
  'copy-document': copyDocument
};

export default icons;
