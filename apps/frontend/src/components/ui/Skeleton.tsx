import type { HTMLAttributes } from 'react';

function Skeleton({ className = '', ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-md bg-soft-gray ${className}`}
      {...rest}
    />
  );
}

export default Skeleton;
