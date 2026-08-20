import { useCallback, useState } from 'react';

export interface CollapsibleWorkspacePanelController {
  isMinimized: boolean;
  isCollapsing: boolean;
  isExpanding: boolean;
  collapse: () => void;
  restore: () => void;
  minimize: () => void;
  handleAnimationEnd: () => void;
}

export function useCollapsibleWorkspacePanel(
  initialMinimized: boolean | (() => boolean) = false,
): CollapsibleWorkspacePanelController {
  const [isMinimized, setIsMinimized] = useState(initialMinimized);
  const [isCollapsing, setIsCollapsing] = useState(false);
  const [isExpanding, setIsExpanding] = useState(false);

  const collapse = useCallback(() => {
    setIsExpanding(false);
    setIsCollapsing(true);
  }, []);

  const restore = useCallback(() => {
    setIsCollapsing(false);
    setIsMinimized(false);
    setIsExpanding(true);
  }, []);

  const minimize = useCallback(() => {
    setIsCollapsing(false);
    setIsExpanding(false);
    setIsMinimized(true);
  }, []);

  const handleAnimationEnd = useCallback(() => {
    if (isCollapsing) {
      setIsMinimized(true);
      setIsCollapsing(false);
    }
    if (isExpanding) setIsExpanding(false);
  }, [isCollapsing, isExpanding]);

  return {
    isMinimized,
    isCollapsing,
    isExpanding,
    collapse,
    restore,
    minimize,
    handleAnimationEnd,
  };
}
