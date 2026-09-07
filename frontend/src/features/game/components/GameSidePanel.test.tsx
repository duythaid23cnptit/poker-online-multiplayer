import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent,render,screen,waitFor } from '@testing-library/react'
import { afterEach,describe,expect,it,vi } from 'vitest'
import { stompSession } from '../../../shared/realtime/stompSession'
import { chatApi } from '../api/chatApi'
import { chatKeys } from '../api/chatQueries'
import { GameSidePanel } from './GameSidePanel'
import type { RoomDetail } from '../../rooms/types/room'

const room={room:{id:7,name:'Real table',ownerUserId:1,roomType:'PUBLIC',passwordRequired:false,status:'PLAYING',seatedPlayers:2,maxPlayers:6,smallBlind:5,bigBlind:10,buyIn:500},members:[]} satisfies RoomDetail
afterEach(()=>vi.restoreAllMocks())
function renderPanel(){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return render(<QueryClientProvider client={client}><GameSidePanel room={room} connected/></QueryClientProvider>)}
describe('GameSidePanel',()=>{
  it('reuses clientMessageId when retrying the same unsent logical message',()=>{vi.spyOn(chatApi,'history').mockResolvedValue([]);vi.spyOn(stompSession,'listen').mockReturnValue(()=>{});const send=vi.spyOn(stompSession,'send').mockReturnValue(false);renderPanel();fireEvent.change(screen.getByLabelText('Room message'),{target:{value:'hello table'}});fireEvent.click(screen.getByRole('button',{name:'Send'}));fireEvent.click(screen.getByRole('button',{name:'Retry'}));expect(send).toHaveBeenCalledTimes(2);expect((send.mock.calls[0][1] as {clientMessageId:string}).clientMessageId).toBe((send.mock.calls[1][1] as {clientMessageId:string}).clientMessageId)})
  it('keeps the chat composer usable when history loading fails',async()=>{vi.spyOn(chatApi,'history').mockRejectedValue(new Error('offline'));vi.spyOn(stompSession,'listen').mockReturnValue(()=>{});vi.spyOn(stompSession,'send').mockReturnValue(true);renderPanel();expect(await screen.findByText('Messages are temporarily unavailable')).toBeInTheDocument();expect(screen.getByLabelText('Room message')).toBeEnabled()})
})

describe('terminal chat authorization', () => {
  it.each(['FINISHED', 'CLOSED'] as const)('does not query, subscribe, or offer Send for a %s room', (status) => {
    const history = vi.spyOn(chatApi, 'history').mockResolvedValue([])
    const listen = vi.spyOn(stompSession, 'listen').mockReturnValue(() => {})
    const client = new QueryClient({defaultOptions:{queries:{retry:false}}})
    render(<QueryClientProvider client={client}><GameSidePanel room={{...room,room:{...room.room,status}}} connected /></QueryClientProvider>)
    expect(screen.queryByLabelText('Room message')).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:'Send'})).not.toBeInTheDocument()
    expect(history).not.toHaveBeenCalled()
    expect(listen).not.toHaveBeenCalled()
    expect(screen.getByText('Session finished · Chat is closed.')).toBeVisible()
    expect(screen.queryByText('No room messages')).not.toBeInTheDocument()
  })

  it('stops member requests and subscriptions on terminal transition while preserving hydrated history', async () => {
    const history = vi.spyOn(chatApi, 'history').mockResolvedValue([])
    const unsubscribe = vi.fn()
    vi.spyOn(stompSession, 'listen').mockReturnValue(unsubscribe)
    const client = new QueryClient({defaultOptions:{queries:{retry:false}}})
    const view = render(<QueryClientProvider client={client}><GameSidePanel room={room} connected /></QueryClientProvider>)
    await waitFor(()=>expect(history).toHaveBeenCalledTimes(1))
    await waitFor(()=>expect(client.isFetching()).toBe(0))
    client.setQueryData(chatKeys.history(7), [{messageId:1,roomId:7,clientMessageId:'test-message',content:'Good hand',createdAt:'2026-09-06T12:00:00Z',sender:{userId:1,displayName:'Player',avatarUrl:null}}])
    view.rerender(<QueryClientProvider client={client}><GameSidePanel room={room} connected live={false} /></QueryClientProvider>)
    expect(screen.getByText('Good hand')).toBeVisible()
    expect(screen.queryByLabelText('Room message')).not.toBeInTheDocument()
    expect(unsubscribe).toHaveBeenCalledTimes(1)
    await client.invalidateQueries({queryKey:chatKeys.history(7)})
    expect(history).toHaveBeenCalledTimes(1)
    fireEvent.keyDown(screen.getByRole('tab',{name:'Chat'}),{key:'ArrowRight'})
    expect(screen.getByRole('tab',{name:'Info'})).toHaveFocus()
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Info')
  })
})
