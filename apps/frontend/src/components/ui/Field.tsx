import { ChevronDown } from 'lucide-react';
import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';

export const inputBaseClassName =
  'h-10 w-full rounded-lg border border-line bg-surface-sunken px-3.5 text-sm text-ink shadow-neu-pressed placeholder:text-text-faint outline-none transition-[background-color,border-color,box-shadow] duration-150 hover:border-line-strong focus-visible:border-primary focus-visible:bg-surface-overlay focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:border-line-subtle disabled:bg-surface-sunken disabled:text-text-faint disabled:shadow-none';

export interface FieldProps {
  label: string;
  htmlFor?: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: ReactNode;
}

function Field({ label, htmlFor, required = false, hint, error, children }: FieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block text-sm font-bold text-text-strong">
        {label}
        {required ? (
          <span aria-hidden="true" className="ml-0.5 text-danger">
            *
          </span>
        ) : null}
      </label>
      <div className="mt-1.5">{children}</div>
      {hint ? <p className="mt-1.5 text-xs text-text-muted">{hint}</p> : null}
      {error ? (
        <p role="alert" className="mt-1.5 text-xs font-bold text-danger-strong">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {}

function Input({ className = '', ...rest }: InputProps) {
  return <input className={`${inputBaseClassName} ${className}`} {...rest} />;
}

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {}

function Textarea({ className = '', ...rest }: TextareaProps) {
  return (
    <textarea className={`${inputBaseClassName} h-auto min-h-24 py-2.5 ${className}`} {...rest} />
  );
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {}

function Select({ className = '', children, ...rest }: SelectProps) {
  return (
    <div className="relative">
      <select className={`${inputBaseClassName} appearance-none pr-9 ${className}`} {...rest}>
        {children}
      </select>
      <ChevronDown
        aria-hidden="true"
        className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
      />
    </div>
  );
}

export { Field, Input, Textarea, Select };
