import { Loader2 } from 'lucide-react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
  children: ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'border border-primary-active/20 bg-primary text-white shadow-neu-raised hover:bg-primary-hover active:bg-primary-active active:shadow-neu-pressed',
  secondary:
    'border border-line bg-surface-raised text-text-strong shadow-neu-raised hover:border-line-strong hover:bg-surface-overlay active:bg-surface-sunken active:shadow-neu-pressed',
  ghost:
    'border border-transparent text-text-strong hover:border-line hover:bg-surface-raised hover:shadow-neu-raised active:bg-surface-sunken active:shadow-neu-pressed',
  danger:
    'border border-danger-strong/25 bg-danger text-white shadow-neu-raised hover:bg-danger-strong active:shadow-neu-pressed',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-8 gap-1.5 px-3 text-xs',
  md: 'h-10 gap-2 px-4 text-sm',
  lg: 'h-11 gap-2 px-5 text-sm',
};

const baseClassName =
  'inline-flex items-center justify-center whitespace-nowrap rounded-lg font-bold transition-[background-color,border-color,color,box-shadow] duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:border-line disabled:bg-surface-sunken disabled:text-text-faint disabled:shadow-none';

export function buttonClassName(
  options: { variant?: ButtonVariant; size?: ButtonSize; className?: string } = {},
): string {
  const { variant = 'primary', size = 'md', className = '' } = options;
  return `${baseClassName} ${variantClasses[variant]} ${sizeClasses[size]} ${className}`;
}

function Button({
  variant = 'primary',
  size = 'md',
  isLoading = false,
  disabled,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled || isLoading}
      className={buttonClassName({ variant, size, className })}
      {...rest}
    >
      {isLoading ? <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" /> : null}
      {children}
    </button>
  );
}

export default Button;
