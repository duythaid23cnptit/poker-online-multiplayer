import { render, screen } from '@testing-library/react'
import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('confirms that the frontend is running', () => {
    render(<HomePage />)

    expect(screen.getByRole('heading', { name: 'Poker Online Multiplayer' })).toBeInTheDocument()
    expect(screen.getByText('Frontend is running.')).toBeInTheDocument()
  })
})

