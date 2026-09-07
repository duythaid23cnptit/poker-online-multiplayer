import { useState } from 'react'
import { Button } from '../../../shared/ui/Button'
import { formatNumber } from '../../../shared/lib/format'
import type { GameViewState, PokerActionType } from '../types/game'
import { useGameActions } from '../hooks/useGameActions'

export function ActionBar({gameId,state,spectator,connected,leaving=false}:{gameId:string;state:GameViewState;spectator:boolean;connected:boolean;leaving?:boolean}){
  const {act,pending,error}=useGameActions(gameId,state);const turn=state.turn
  if(leaving)return <div className="action-waiting"><strong>Leaving after this hand</strong><span>Your remaining chips will return to your account when this hand ends.</span></div>
  if(spectator)return <div className="spectator-bar"><strong>Spectator mode</strong><span>Public table state only. Player actions and private cards are unavailable.</span></div>
  if(state.publicState?.handCompleted)return <div className="action-waiting"><strong>Next hand starting soon…</strong><span>The table is settling the completed hand.</span></div>
  if(!turn)return <div className="action-waiting"><strong>{connected?'Waiting for your turn':'Reconnecting…'}</strong><span>Your available actions will appear here.</span></div>
  const wager=turn.legalActions.includes('BET')?'BET':turn.legalActions.includes('RAISE')?'RAISE':null
  const actionButton=(action:PokerActionType,label:string)=><Button key={action} type="button" variant={action==='CHECK'||action==='CALL'?'primary':'secondary'} className={action==='FOLD'?'action-fold':undefined} disabled={pending||!connected} onClick={()=>act(action)}>{label}</Button>
  return <div className="action-bar" aria-busy={pending}>{pending&&<p className="action-pending" role="status">Submitting action…</p>}{error&&<p className="action-error" role="alert">{error}</p>}<div className="action-buttons">{turn.legalActions.includes('FOLD')&&actionButton('FOLD','Fold')}{turn.legalActions.includes('CHECK')&&actionButton('CHECK','Check')}{turn.legalActions.includes('CALL')&&actionButton('CALL',`Call ${formatNumber(turn.callAmount)}`)}{turn.legalActions.includes('ALL_IN')&&actionButton('ALL_IN','All in')}</div>{wager&&<WagerControl key={turn.turnId} type={wager} minimum={turn.minimumTarget} maximum={turn.maximumTarget} pending={pending} connected={connected} act={act}/>}</div>
}

function WagerControl({type,minimum,maximum,pending,connected,act}:{type:'BET'|'RAISE';minimum:number;maximum:number;pending:boolean;connected:boolean;act:(action:PokerActionType,amount?:number)=>void}){const [amount,setAmount]=useState(minimum);return <div className="raise-control"><label htmlFor="wagerAmount">{type==='BET'?'Bet total':'Raise to total'} <strong>{formatNumber(amount)}</strong></label><input id="wagerAmount" disabled={pending||!connected} type="range" min={minimum} max={maximum} value={amount} onChange={(event)=>setAmount(Number(event.target.value))}/><p className="wager-help">Total chips committed this round, including your current bet.</p><div><span>Min {formatNumber(minimum)}</span><span>Max {formatNumber(maximum)}</span></div><Button type="button" loading={pending} disabled={!connected} onClick={()=>act(type,amount)}>{type==='BET'?'Bet':'Raise'} {formatNumber(amount)}</Button></div>}
