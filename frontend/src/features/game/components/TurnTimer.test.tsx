import { render,screen } from '@testing-library/react'
import { describe,expect,it } from 'vitest'
import { TurnTimer } from './TurnTimer'
describe('TurnTimer',()=>{it('presents server-reported remaining time without expiring the turn locally',()=>{render(<TurnTimer timer={{handId:1,turnId:'turn',currentTurnSeat:2,remainingSeconds:4,deadline:'2026-09-01T00:00:04Z'}}/>);expect(screen.getByRole('timer',{name:'4 seconds remaining'})).toHaveTextContent('4s')})})
