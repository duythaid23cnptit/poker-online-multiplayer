import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Avatar } from './Avatar'

describe('Avatar', () => {
  it('renders only absolute HTTP(S) image sources and falls back safely otherwise', () => {
    const { rerender } = render(<Avatar displayName="River Reader" src="/api/private" />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()

    rerender(<Avatar displayName="River Reader" src="https://images.example.com/avatar.png" />)
    expect(screen.getByRole('img', { name: "River Reader's avatar" })).toHaveAttribute(
      'src',
      'https://images.example.com/avatar.png',
    )

    rerender(<Avatar displayName="River Reader" src="javascript:alert(1)" />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
