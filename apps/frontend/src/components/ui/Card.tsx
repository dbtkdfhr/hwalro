import type { HTMLAttributes } from 'react';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  interactive?: boolean;
  padded?: boolean;
}

function Card({
  interactive = false,
  padded = true,
  className = '',
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={`rounded-xl border border-line bg-surface-raised shadow-neu-raised ${
        interactive
          ? 'transition-[border-color,background-color,box-shadow] duration-150 hover:border-line-strong hover:bg-surface-overlay hover:shadow-neu-floating active:bg-surface-sunken active:shadow-neu-pressed'
          : ''
      } ${padded ? 'p-5' : ''} ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}

export default Card;
