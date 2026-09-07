export interface ChatSender { userId:number; displayName:string; avatarUrl:string|null }
export interface ChatMessage { messageId:number; roomId:number; clientMessageId:string; content:string; createdAt:string; sender:ChatSender }
