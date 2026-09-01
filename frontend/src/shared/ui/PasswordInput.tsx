import { forwardRef, useState, type InputHTMLAttributes } from 'react'
import { Input } from './Input'

export interface PasswordInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  invalid?: boolean
}

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(function PasswordInput(
  { className = '', ...props },
  ref,
) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative">
      <Input ref={ref} type={visible ? 'text' : 'password'} className={`pr-16 ${className}`} {...props} />
      <button
        type="button"
        className="absolute inset-y-0 right-0 px-3 text-xs font-bold text-secondary transition hover:text-text focus-visible:rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? 'Hide password' : 'Show password'}
        aria-pressed={visible}
      >
        {visible ? 'Hide' : 'Show'}
      </button>
    </div>
  )
})
