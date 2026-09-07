import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../types/chat'
import { CHAT_CACHE_LIMIT, chatKeys, mergeChatMessages } from './chatQueries'

function message(messageId: number, roomId = 7, senderId = 3, clientMessageId = `command-${messageId}`): ChatMessage {
  return { messageId, roomId, clientMessageId, content: `message ${messageId}`,
    createdAt: new Date(Date.UTC(2026, 8, 4, 0, 0, messageId)).toISOString(),
    sender: { userId: senderId, displayName: `Player ${senderId}`, avatarUrl: null } }
}

describe('chat query reconciliation', () => {
  it('uses a room-scoped stable history key', () => {
    expect(chatKeys.history(7)).toEqual(['room-chat', 7, 'history'])
    expect(chatKeys.history(8)).not.toEqual(chatKeys.history(7))
  })

  it('merges deterministically and deduplicates by message and scoped command identity', () => {
    const original = message(2, 7, 3, 'same-command')
    const duplicateCommand = { ...original, messageId: 20 }
    expect(mergeChatMessages(7, [original, message(3)], [message(1), duplicateCommand, message(3)])
      .map((item) => item.messageId)).toEqual([1, 20, 3])
  })

  it('does not confuse equal client IDs from different senders or leak another room', () => {
    expect(mergeChatMessages(7, [message(1, 7, 3, 'same'), message(2, 7, 4, 'same'), message(3, 8)])
      .map((item) => item.messageId)).toEqual([1, 2])
  })

  it('keeps only the newest bounded cache window', () => {
    const messages = Array.from({ length: CHAT_CACHE_LIMIT + 5 }, (_, index) => message(index + 1))
    expect(mergeChatMessages(7, messages)).toHaveLength(CHAT_CACHE_LIMIT)
    expect(mergeChatMessages(7, messages)[0].messageId).toBe(6)
  })
})
