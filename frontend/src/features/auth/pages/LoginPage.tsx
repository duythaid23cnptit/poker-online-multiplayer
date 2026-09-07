import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { routes, safeAppReturnPath } from '../../../app/router/routePaths'
import { ApiClientError, getErrorMessage } from '../../../shared/api/apiError'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { PasswordInput } from '../../../shared/ui/PasswordInput'
import { useLogin } from '../hooks/useLogin'
import { loginSchema, type LoginFormValues } from '../schemas/authSchemas'

interface LoginLocationState { from?: string; registered?: boolean }

export function LoginPage() {
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state || {}) as LoginLocationState
  const { register, handleSubmit, setError, formState: { errors } } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema), mode: 'onTouched', defaultValues: { username: '', password: '' },
  })

  const submit = handleSubmit(async (values) => {
    if (login.isPending) return
    try {
      await login.mutateAsync(values)
      navigate(safeAppReturnPath(state.from), { replace: true })
    } catch (error) {
      if (error instanceof ApiClientError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'username' || fieldError.field === 'password') {
            setError(fieldError.field, { message: fieldError.message })
          }
        }
      }
    }
  })

  return (
    <Card className="auth-card p-6 sm:p-9">
      <div className="auth-card-heading">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Welcome back <span aria-hidden="true">✦</span></p>
        <h1 className="mt-3 text-3xl font-black tracking-tight text-text">Sign in to play</h1>
        <p className="mt-3 text-sm leading-6 text-secondary">Continue to your secure Poker Online account.</p>
      </div>
      {state.registered && (
        <div className="mt-6 rounded-control border border-success/30 bg-success/10 px-4 py-3 text-sm text-success" role="status">
          Account created. Sign in with your new credentials.
        </div>
      )}
      {login.isError && (
        <div className="mt-6 rounded-control border border-danger/25 bg-danger/8 px-4 py-3 text-sm text-danger" role="alert">
          {getErrorMessage(login.error)}
        </div>
      )}
      <form className="mt-8 space-y-5" onSubmit={submit} noValidate>
        <FormField reserveErrorSpace id="username" label="Username" error={errors.username?.message}>
          <Input
            id="username" autoComplete="username" placeholder="Your username"
            invalid={Boolean(errors.username)} aria-describedby={errors.username ? 'username-error' : undefined}
            disabled={login.isPending} {...register('username')}
          />
        </FormField>
        <FormField reserveErrorSpace id="password" label="Password" error={errors.password?.message}>
          <PasswordInput
            id="password" autoComplete="current-password" placeholder="Your password"
            invalid={Boolean(errors.password)} aria-describedby={errors.password ? 'password-error' : undefined}
            disabled={login.isPending} {...register('password')}
          />
        </FormField>
        <Button type="submit" className="w-full" loading={login.isPending} loadingLabel="Signing in…">
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-secondary">
        New to the table?{' '}
        <Link className="font-bold text-accent underline-offset-4 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus" to={routes.register}>
          Create an account
        </Link>
      </p>
    </Card>
  )
}
