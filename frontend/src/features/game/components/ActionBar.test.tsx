import { fireEvent,render,screen,waitFor } from '@testing-library/react'
import { afterEach,describe,expect,it,vi } from 'vitest'
import { stompSession } from '../../../shared/realtime/stompSession'
import { initialGameState } from '../realtime/gameEventReducer'
import type { PokerActionType } from '../types/game'
import { ActionBar } from './ActionBar'

const gameId='11111111-1111-4111-8111-111111111111'
afterEach(()=>vi.restoreAllMocks())
describe('ActionBar',()=>{
  it('renders only server-provided legal actions and sends one intent',()=>{
    const state={...initialGameState(7,gameId),turn:{handId:1,turnId:'22222222-2222-4222-8222-222222222222',legalActions:['FOLD','CHECK'] as PokerActionType[],callAmount:0,minimumTarget:0,maximumTarget:0,tableChips:500}}
    const send=vi.spyOn(stompSession,'send').mockReturnValue(true)
    render(<ActionBar gameId={gameId} state={state} spectator={false} connected/>)
    expect(screen.getByRole('button',{name:'Check'})).toBeVisible();expect(screen.queryByRole('button',{name:/Raise/})).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button',{name:'Check'}));expect(send).toHaveBeenCalledTimes(1);expect(send.mock.calls[0][0]).toBe(`/app/game/${gameId}/action`)
  })
  it('hides controls for spectators',()=>{render(<ActionBar gameId={gameId} state={initialGameState(7,gameId)} spectator connected/>);expect(screen.getByText('Spectator mode')).toBeVisible();expect(screen.queryByRole('button',{name:'Fold'})).not.toBeInTheDocument()})
  it('hides action controls after authoritative departure acknowledgement',()=>{
    const state={...initialGameState(7,gameId),turn:{handId:1,turnId:'22222222-2222-4222-8222-222222222222',legalActions:['FOLD','CHECK'] as PokerActionType[],callAmount:0,minimumTarget:0,maximumTarget:0,tableChips:500}}
    render(<ActionBar gameId={gameId} state={state} spectator={false} connected leaving/>)
    expect(screen.getByText('Leaving after this hand')).toBeVisible()
    expect(screen.queryByRole('button',{name:'Check'})).not.toBeInTheDocument()
  })
  it('shows a neutral settlement state after a hand completes',()=>{
    const state={...initialGameState(7,gameId),publicState:{handId:1,handNumber:1,phase:'FINISHED' as const,dealerSeat:1,smallBlindSeat:1,bigBlindSeat:2,currentTurnUserId:null,currentBet:0,minimumRaise:20,communityCards:[],players:[],handCompleted:true,sessionFinished:false}}
    render(<ActionBar gameId={gameId} state={state} spectator={false} connected/>)
    expect(screen.getByText('Next hand starting soon…')).toBeVisible()
    expect(screen.queryByText('Waiting for your turn')).not.toBeInTheDocument()
  })
  it('removes Working state after the matching authoritative action',async()=>{
    const state={...initialGameState(7,gameId),turn:{handId:1,turnId:'22222222-2222-4222-8222-222222222222',legalActions:['FOLD','RAISE'] as PokerActionType[],callAmount:0,minimumTarget:20,maximumTarget:500,tableChips:500}}
    const send=vi.spyOn(stompSession,'send').mockReturnValue(true)
    const view=render(<ActionBar gameId={gameId} state={state} spectator={false} connected/>)
    fireEvent.click(screen.getByRole('button',{name:'Raise 20'}))
    expect(screen.getByRole('button',{name:/Working/})).toBeDisabled()
    const clientActionId=(send.mock.calls[0][1] as {clientActionId:string}).clientActionId

    view.rerender(<ActionBar gameId={gameId} state={{...state,lastAction:{userId:42,seat:1,actionType:'RAISE',amount:20,resultingCurrentBet:20,resultingTableChips:480,clientActionId,automatic:false}}} spectator={false} connected/>)

    await waitFor(()=>expect(screen.getByRole('button',{name:'Raise 20'})).toBeEnabled())
  })
})
