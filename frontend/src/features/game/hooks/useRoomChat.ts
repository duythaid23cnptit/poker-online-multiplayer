import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { stompSession } from '../../../shared/realtime/stompSession'
import { chatHistoryQueryOptions, chatKeys, mergeChatMessages } from '../api/chatQueries'
import type { ChatMessage } from '../types/chat'

function parseChat(body:string,roomId:number):ChatMessage|null { try { const event=JSON.parse(body) as {protocolVersion?:number;type?:string;scope?:{roomId?:number};payload?:ChatMessage}; return event.protocolVersion===1&&event.type==='CHAT_MESSAGE'&&event.scope?.roomId===roomId&&event.payload?.roomId===roomId ? event.payload : null } catch{return null} }
export function useRoomChat(roomId:number, enabled = true) {
  const queryClient=useQueryClient();const history=useQuery({...chatHistoryQueryOptions(roomId), enabled});type Pending={roomId:number;clientMessageId:string;failed:boolean};const [pending,setPending]=useState<Pending|null>(null);const pendingRef=useRef<Pending|null>(null)
  useEffect(()=> enabled ? stompSession.listen(`/topic/room/${roomId}`,(body)=>{const message=parseChat(body,roomId);if(!message)return;queryClient.setQueryData<ChatMessage[]>(chatKeys.history(roomId),(current)=>mergeChatMessages(roomId,current??[],[message]));const current=pendingRef.current;if(current?.roomId===roomId&&current.clientMessageId===message.clientMessageId){pendingRef.current=null;setPending(null)}}) : undefined,[enabled,queryClient,roomId])
  const send=useCallback((content:string,retryId?:string)=>{const clientMessageId=retryId||crypto.randomUUID();const sent=enabled && stompSession.send(`/app/room/${roomId}/chat`,{clientMessageId,content});const next={roomId,clientMessageId,failed:!sent};pendingRef.current=next;setPending(next);return {clientMessageId,sent}},[enabled,roomId])
  const activePending=pending?.roomId===roomId?pending:null
  return {messages:history.data??[],pendingId:activePending?.clientMessageId??null,failed:activePending?.failed??false,send,historyPending:enabled && history.isPending,historyError:history.error}
}
