/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  $createParagraphNode,
  $getSelection,
  $isRangeSelection,
  DEPRECATED_$isGridSelection,
  LexicalEditor,
} from 'lexical';
import {
  INSERT_ORDERED_LIST_COMMAND,
  INSERT_UNORDERED_LIST_COMMAND,
  REMOVE_LIST_COMMAND,
} from '@lexical/list';
import {$createHeadingNode, HeadingTagType} from '@lexical/rich-text';
import {$setBlocksType} from '@lexical/selection';

import {Dropdown} from 'components';
import {t} from 'translation';

export const BLOCK_TYPES = ['paragraph', 'h1', 'h2', 'h3', 'number', 'bullet'] as const;

export default function BlockTypeDropdown({
  editor,
  blockType,
  disabled = false,
}: {
  editor: LexicalEditor;
  blockType: string;
  disabled?: boolean;
}) {
  const formatParagraph = () => {
    if (blockType !== 'paragraph') {
      editor.update(() => {
        const selection = $getSelection();
        if ($isRangeSelection(selection) || DEPRECATED_$isGridSelection(selection)) {
          $setBlocksType(selection, () => $createParagraphNode());
        }
      });
    }
  };

  const formatHeading = (headingSize: HeadingTagType) => () => {
    if (blockType !== headingSize) {
      editor.update(() => {
        const selection = $getSelection();
        if ($isRangeSelection(selection) || DEPRECATED_$isGridSelection(selection)) {
          $setBlocksType(selection, () => $createHeadingNode(headingSize));
        }
      });
    }
  };

  const formatBulletList = () => {
    if (blockType !== 'bullet') {
      editor.dispatchCommand(INSERT_UNORDERED_LIST_COMMAND, undefined);
    } else {
      editor.dispatchCommand(REMOVE_LIST_COMMAND, undefined);
    }
  };

  const formatNumberedList = () => {
    if (blockType !== 'number') {
      editor.dispatchCommand(INSERT_ORDERED_LIST_COMMAND, undefined);
    } else {
      editor.dispatchCommand(REMOVE_LIST_COMMAND, undefined);
    }
  };

  const FORMATTERS = {
    bullet: formatBulletList,
    h1: formatHeading('h1'),
    h2: formatHeading('h2'),
    h3: formatHeading('h3'),
    number: formatNumberedList,
    paragraph: formatParagraph,
  };

  if (!BLOCK_TYPES.some((type) => type === blockType)) {
    return null;
  }

  return (
    <Dropdown
      small
      disabled={disabled}
      label={t(`textEditor.toolbar.blockStyles.${blockType}`)}
      className="BlockTypeOptions"
    >
      {BLOCK_TYPES.map((key) => {
        return (
          <Dropdown.Option key={key} active={blockType === key} onClick={FORMATTERS[key]}>
            {t(`textEditor.toolbar.blockStyles.${key}`)}
          </Dropdown.Option>
        );
      })}
    </Dropdown>
  );
}
