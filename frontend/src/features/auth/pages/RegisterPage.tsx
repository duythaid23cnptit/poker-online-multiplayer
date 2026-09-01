import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { ApiClientError, getErrorMessage } from '../../../shared/api/apiError'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { PasswordInput } from '../../../shared/ui/PasswordInput'
import { useRegister } from '../hooks/useRegister'
import { registerSchema, type RegisterFormValues } from '../schemas/authSchemas'

export function RegisterPage() {
  const registration = useRegister()
  const navigate = useNavigate()
  const { register, handleSubmit, setError, formState: { errors } } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema), mode: 'onTouched',
    defaultValues: { username: '', displayName: '', email: '', password: '' },
  })

  const submit = handleSubmit(async (values) => {
    if (registration.isPending) return
    try {
      await registration.mutateAsync({
        username: values.username.trim(), displayName: values.displayName.trim(),
        email: values.email.trim() || null, password: values.password,
      })
      navigate(routes.login, { replace: true, state: { registered: true } })
    } catch (error) {
      if (error instanceof ApiClientError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'username' || fieldError.field === 'displayName' || fieldError.field === 'email' || fieldError.field === 'password') {
            setError(fieldError.field, { message: fieldError.message })
          }
        }
      }
    }
  })

  return (
    <Card className="auth-card p-6 sm:p-9">
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Join Poker Online <span aria-hidden="true">✦</span></p>
      <h1 className="mt-3 text-3xl font-black tracking-tight text-text">Create your account</h1>
      <p className="mt-3 text-sm leading-6 text-secondary">Set up the identity you&apos;ll bring to every table.</p>
      {registration.isError && (
        <div className="mt-6 rounded-control border border-danger/25 bg-danger/8 px-4 py-3 text-sm text-danger" role="alert">
          {getErrorMessage(registration.error)}
        </div>
      )}
      <form className="mt-8 space-y-5" onSubmit={submit} noValidate>
        <FormField id="username" label="Username" hint="Letters, numbers, underscore" error={errors.username?.message}>
          <Input id="username" autoComplete="username" placeholder="Choose a username" invalid={Boolean(errors.username)} aria-describedby={`username-hint${errors.username ? ' username-error' : ''}`} disabled={registration.isPending} {...register('username')} />
        </FormField>
        <FormField id="displayName" label="Display name" error={errors.displayName?.message}>
          <Input id="displayName" autoComplete="nickname" placeholder="Name shown at the table" invalid={Boolean(errors.displayName)} aria-describedby={errors.displayName ? 'displayName-error' : undefined} disabled={registration.isPending} {...register('displayName')} />
        </FormField>
        <FormField id="email" label="Email" hint="Optional" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" placeholder="you@example.com" invalid={Boolean(errors.email)} aria-describedby={`email-hint${errors.email ? ' email-error' : ''}`} disabled={registration.isPending} {...register('email')} />
        </FormField>
        <FormField id="password" label="Password" hint="8–72 characters" error={errors.password?.message}>
          <PasswordInput id="password" autoComplete="new-password" placeholder="Create a password" invalid={Boolean(errors.password)} aria-describedby={`password-hint${errors.password ? ' password-error' : ''}`} disabled={registration.isPending} {...register('password')} />
        </FormField>
        <Button type="submit" className="w-full" loading={registration.isPending} loadingLabel="Creating account…">Create account</Button>
      </form>
      <p className="mt-6 text-center text-sm text-secondary">
        Already have an account?{' '}
        <Link className="font-bold text-accent underline-offset-4 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus" to={routes.login}>Sign in</Link>
      </p>
    </Card>
  )
}
