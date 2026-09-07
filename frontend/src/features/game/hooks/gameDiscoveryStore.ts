import { create } from 'zustand'
interface GameDiscoveryState { roomId: number | null; gameId: string | null; discover: (roomId:number,gameId:string)=>void; clear:()=>void }
export const useGameDiscoveryStore = create<GameDiscoveryState>((set) => ({ roomId:null, gameId:null, discover:(roomId,gameId)=>set({roomId,gameId}), clear:()=>set({roomId:null,gameId:null}) }))
