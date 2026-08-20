import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import type { Vec2 } from '../types';
import { TEXT_FONT_PX } from '../utils/hitTest';
import { estimateTextWidthPx, PX_PER_METER } from '../utils/geometry';

interface InlineTextInputProps {
  point: Vec2;
  zoom: number;
  panX: number;
  panY: number;
  initialText: string;
  onCommit: (text: string) => void;
  onCancel: () => void;
}

const MIN_INPUT_WIDTH = 80;

export function InlineTextInput({
  point,
  zoom,
  panX,
  panY,
  initialText,
  onCommit,
  onCancel,
}: InlineTextInputProps) {
  const [mounted, setMounted] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const resizeToContent = () => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = `${textarea.scrollHeight}px`;
      textarea.style.width = `${MIN_INPUT_WIDTH}px`;
      textarea.style.width = `${Math.max(width, textarea.scrollWidth)}px`;
    }
  };

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (mounted) {
      const textarea = textareaRef.current;
      if (textarea) {
        textarea.focus();
        textarea.select();
        resizeToContent();
      }
    }
  }, [mounted]);

  if (!mounted) {
    return null;
  }

  const longestLine = initialText
    .split('\n')
    .reduce((longest, line) => (line.length > longest.length ? line : longest));
  const width = Math.max(MIN_INPUT_WIDTH, estimateTextWidthPx(longestLine, TEXT_FONT_PX * zoom));

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Escape') {
      onCancel();
      return;
    }
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      const value = event.currentTarget.value;
      onCommit(value);
      event.currentTarget.blur();
    }
  };

  return (
    <textarea
      ref={textareaRef}
      aria-label="텍스트 입력"
      className="absolute z-10 rounded-sm border-2 border-primary bg-white font-sans text-ink shadow-[0_2px_8px_rgba(0,0,0,0.15)] outline-none"
      defaultValue={initialText}
      style={{
        left: (point.x - panX) * zoom * PX_PER_METER,
        top: (point.y - panY) * zoom * PX_PER_METER,
        fontSize: TEXT_FONT_PX * zoom,
        lineHeight: 1.2,
        width,
        overflow: 'hidden',
        resize: 'none',
        whiteSpace: 'pre',
        padding: '2px 4px',
      }}
      onKeyDown={handleKeyDown}
      onChange={resizeToContent}
      onBlur={(event) => onCommit(event.target.value)}
    />
  );
}
