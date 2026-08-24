import type { HTMLAttributes } from 'react';

function Skeleton({ className = '', ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-lg border border-line-subtle bg-surface-sunken shadow-neu-pressed ${className}`}
      {...rest}
    />
  );
}

export default Skeleton;
