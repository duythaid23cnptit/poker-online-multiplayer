import { render,screen } from '@testing-library/react'
import { describe,expect,it } from 'vitest'
import { PlayingCard } from './PlayingCard'
describe('PlayingCard',()=>{it('renders an authoritative face-up card accessibly',()=>{render(<PlayingCard card={{rank:'ACE',suit:'HEARTS'}}/>);expect(screen.getByLabelText('ace of hearts')).toHaveTextContent('A')});it('does not expose a hidden card value',()=>{render(<PlayingCard card={{rank:'ACE',suit:'HEARTS'}} hidden/>);expect(screen.getByLabelText('Hidden card')).toBeVisible();expect(screen.queryByLabelText('ace of hearts')).not.toBeInTheDocument()})})
