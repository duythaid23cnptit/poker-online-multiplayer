package com.ptit.poker.analytics.domain.ranking;
import java.util.*;
public final class MultiplayerEloCalculator {
 public static final int INITIAL_RATING=1000,K_FACTOR=32;
 public List<Change> calculate(List<Result> input){if(input.size()<2)throw new IllegalArgumentException("at least two participants required");
  List<Result> players=List.copyOf(input);Set<Long> ids=new HashSet<>();for(Result p:players)if(!ids.add(p.userId()))throw new IllegalArgumentException("duplicate user");
  List<Change> out=new ArrayList<>();for(Result p:players){double expected=0,actual=0;for(Result q:players)if(q.userId()!=p.userId()){
   expected+=1d/(1d+Math.pow(10d,(q.oldRating()-p.oldRating())/400d));actual+=p.sessionNet()>q.sessionNet()?1:p.sessionNet()==q.sessionNet()?.5:0;}
   int raw=(int)Math.round(K_FACTOR*((actual/(players.size()-1))-(expected/(players.size()-1))));int next=Math.max(0,p.oldRating()+raw);
   int placement=1+(int)players.stream().filter(q->q.sessionNet()>p.sessionNet()).count();out.add(new Change(p.userId(),p.oldRating(),next,next-p.oldRating(),p.sessionNet(),placement,players.size()));}
  return List.copyOf(out);}
 public record Result(long userId,int oldRating,long sessionNet){} public record Change(long userId,int oldRating,int newRating,int ratingDelta,long sessionNet,int placement,int participantCount){}
}
