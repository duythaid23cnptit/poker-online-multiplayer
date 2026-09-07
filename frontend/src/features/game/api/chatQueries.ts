import { queryOptions } from '@tanstack/react-query'
import type { ChatMessage } from '../types/chat'
import { chatApi } from './chatApi'

export const CHAT_HISTORY_LIMIT = 50
export const CHAT_CACHE_LIMIT = 100

export const chatKeys = {
  all: ['room-chat'] as const,
  history: (roomId: number) => [...chatKeys.all, roomId, 'history'] as const,
}

function commandKey(message: ChatMessage) {
  return `${message.roomId}:${message.sender.userId}:${message.clientMessageId}`
}

export function mergeChatMessages(roomId: number, ...sources: ReadonlyArray<readonly ChatMessage[]>): ChatMessage[] {
  const merged: ChatMessage[] = []
  for (const message of sources.flat()) {
    if (message.roomId !== roomId) continue
    const index = merged.findIndex((candidate) =>
      candidate.messageId === message.messageId || commandKey(candidate) === commandKey(message))
    if (index >= 0) merged[index] = message
    else merged.push(message)
  }
  merged.sort((left, right) => left.createdAt.localeCompare(right.createdAt) || left.messageId - right.messageId)
  return merged.slice(-CHAT_CACHE_LIMIT)
}

export function chatHistoryQueryOptions(roomId: number) {
  return queryOptions({
    queryKey: chatKeys.history(roomId),
    queryFn: ({ signal }) => chatApi.history(roomId, CHAT_HISTORY_LIMIT, signal),
    staleTime: 15_000,
    refetchOnMount: 'always',
    refetchOnReconnect: 'always',
    structuralSharing: (previous, incoming) => mergeChatMessages(
      roomId,
      Array.isArray(previous) ? previous as ChatMessage[] : [],
      Array.isArray(incoming) ? incoming as ChatMessage[] : [],
    ),
  })
}
