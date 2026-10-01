import { render,screen } from '@testing-library/react'
import { describe,expect,it } from 'vitest'
import { initialGameState } from '../realtime/gameEventReducer'
import type { GameViewState } from '../types/game'
import type { RoomDetail } from '../../rooms/types/room'
import { PokerTable } from './PokerTable'
import { historicalSeatCapacity } from '../utils/seatLayout'

const gameId='11111111-1111-4111-8111-111111111111'
const room={room:{id:7,name:'Table',ownerUserId:42,roomType:'PUBLIC',passwordRequired:false,status:'PLAYING',seatedPlayers:2,maxPlayers:6,smallBlind:5,bigBlind:10,buyIn:500},members:[{userId:42,username:'Hero',seatNumber:1,state:'PLAYING',tableChips:480},{userId:99,username:'Opponent',seatNumber:2,state:'PLAYING',tableChips:520}]} satisfies RoomDetail
const state={...initialGameState(7,gameId),holeCards:[{rank:'ACE',suit:'SPADES'},{rank:'KING',suit:'HEARTS'}],publicState:{handId:1,handNumber:1,phase:'FLOP',dealerSeat:1,smallBlindSeat:1,bigBlindSeat:2,currentTurnUserId:99,currentBet:20,minimumRaise:20,communityCards:[{rank:'TWO',suit:'CLUBS'}],players:[{userId:42,seat:1,tableChips:480,currentBet:20,totalCommitted:null,participation:'ACTIVE',connected:true,leaving:false},{userId:99,seat:2,tableChips:520,currentBet:20,totalCommitted:null,participation:'ACTIVE',connected:true,leaving:false}],handCompleted:false,sessionFinished:false}} satisfies GameViewState
describe('PokerTable',()=>{it('renders server-backed seats, community cards and only the user private cards',()=>{render(<PokerTable state={state} room={room} ownUserId={42}/>);expect(screen.getByText('Hero')).toBeVisible();expect(screen.getByText('Opponent')).toBeVisible();expect(screen.getByLabelText('two of clubs')).toBeVisible();expect(screen.getByLabelText('ace of spades')).toBeVisible();expect(screen.getByLabelText('king of hearts')).toBeVisible();expect(screen.getAllByText('Your cards')).toHaveLength(1)});it('does not manufacture a table when no authoritative state was received',()=>{render(<PokerTable state={initialGameState(7,gameId)} room={room} ownUserId={42}/>);expect(screen.getByText('Awaiting authoritative game state')).toBeVisible();expect(screen.queryByText('Opponent')).not.toBeInTheDocument()});it('uses deterministic capacity and player labels without room presentation data',()=>{render(<PokerTable state={state} ownUserId={42}/>);expect(screen.getByText('Player #42')).toBeVisible();expect(screen.getByText('Player #99')).toBeVisible();expect(historicalSeatCapacity([])).toBe(2);expect(historicalSeatCapacity([1,6,3])).toBe(6)})})

describe('authoritative table presentation', () => {
  it('shows authoritative player names to a spectator', () => {
    render(<PokerTable state={state} room={room} ownUserId={777} />)
    expect(screen.getByText('Hero')).toBeVisible()
    expect(screen.getByText('Opponent')).toBeVisible()
    expect(screen.queryByText('Player #99')).not.toBeInTheDocument()
  })

  it('does not reuse identities from a different room', () => {
    const staleRoom = { ...room, room: { ...room.room, id: 8 }, members: [
      room.members[0], { ...room.members[1], username: 'Stale opponent' },
    ] }
    render(<PokerTable state={state} room={staleRoom} ownUserId={42} />)
    expect(screen.queryByText('Stale opponent')).not.toBeInTheDocument()
    expect(screen.getByText('Player #99')).toBeVisible()
  })

  it('includes earlier streets and folded commitments in the pot', () => {
    const current: GameViewState = {...state, publicState:{...state.publicState,players:state.publicState.players.map((player,index)=>({...player,currentBet:10,totalCommitted:index?120:80,participation:index?'FOLDED':'ACTIVE'}))}}
    render(<PokerTable state={current} room={room} ownUserId={42} />)
    expect(screen.getByLabelText('Current pot')).toHaveTextContent('200')
  })
  it('does not invent a pot if commitment data is absent', () => {
    render(<PokerTable state={state} room={room} ownUserId={42} />)
    expect(screen.queryByLabelText('Current pot')).not.toBeInTheDocument()
  })
  it('shows actual split payouts and distinguishes side pots', () => {
    const current: GameViewState = {...state,result:{handId:1,endReason:'SHOWDOWN',finalPlayers:state.publicState.players,uncalledReturns:[],awards:[
      {potIndex:0,potType:'MAIN',potAmount:100,winnerUserIds:[42,99],baseShare:50,oddChipUserIds:[],winnerPayouts:{'42':50,'99':50}},
      {potIndex:1,potType:'SIDE',potAmount:60,winnerUserIds:[99],baseShare:60,oddChipUserIds:[],winnerPayouts:{'99':60}},
    ]}}
    render(<PokerTable state={current} room={room} ownUserId={42} />)
    expect(screen.getByText('Showdown complete')).toBeVisible()
    expect(screen.getByText('Main pot · 100 chips')).toBeVisible()
    expect(screen.getByText('Hero +50 · Opponent +50')).toBeVisible()
    expect(screen.getByText('Side pot 1 · 60 chips')).toBeVisible()
    expect(screen.getByText('Opponent +60')).toBeVisible()
  })
  it.each([2,3,6,9])('lays out %i occupied seats with distinct positions and preserves private-card isolation', (count) => {
    const players=Array.from({length:count},(_,index)=>({...state.publicState.players[0],userId:index+1,seat:index+1}))
    const current:GameViewState={...state,holeCards:[],publicState:{...state.publicState,players}}
    const {container}=render(<PokerTable state={current} ownUserId={999} />)
    const seats=Array.from(container.querySelectorAll<HTMLElement>('.game-seat'))
    expect(seats).toHaveLength(count)
    expect(new Set(seats.map((seat)=>seat.style.cssText)).size).toBe(count)
    expect(screen.queryByText('Your cards')).not.toBeInTheDocument()
  })
})
