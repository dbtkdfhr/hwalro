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
      className={`rounded-xl border border-line bg-surface ${
        interactive
          ? 'transition-colors duration-150 hover:border-line-strong hover:bg-primary-faint'
          : ''
      } ${padded ? 'p-5' : ''} ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}

export default Card;
