import {
  type ChangeEventHandler,
  type ComponentProps,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
} from 'react';

type AutoResizeTextareaProps = Omit<ComponentProps<'textarea'>, 'onChange' | 'value'> & {
  value: string;
  onChange: ChangeEventHandler<HTMLTextAreaElement>;
};

function AutoResizeTextarea({
  value,
  onChange,
  onBlur,
  onFocus,
  style,
  ...textareaProps
}: AutoResizeTextareaProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const minimumHeightRef = useRef(0);

  const resizeToContent = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    if (minimumHeightRef.current === 0) {
      minimumHeightRef.current = textarea.offsetHeight;
    }

    textarea.style.height = 'auto';
    textarea.style.height = `${Math.max(textarea.scrollHeight, minimumHeightRef.current)}px`;
  }, []);

  useLayoutEffect(() => {
    resizeToContent();
  }, [resizeToContent, value]);

  useEffect(() => {
    window.addEventListener('resize', resizeToContent);
    return () => window.removeEventListener('resize', resizeToContent);
  }, [resizeToContent]);

  return (
    <textarea
      {...textareaProps}
      ref={textareaRef}
      value={value}
      onChange={onChange}
      onFocus={(event) => {
        onFocus?.(event);
        window.requestAnimationFrame(resizeToContent);
      }}
      onBlur={(event) => {
        onBlur?.(event);
        window.requestAnimationFrame(resizeToContent);
      }}
      style={{ ...style, overflowY: 'hidden' }}
    />
  );
}

export default AutoResizeTextarea;
