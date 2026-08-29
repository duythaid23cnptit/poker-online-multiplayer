package com.ptit.poker.admin.application;

import java.math.BigDecimal;import java.time.Instant;import java.util.List;

public final class AdminModels {private AdminModels(){}
 public record Page<T>(List<T> items,int page,int size,long total){}
 public record Overview(long totalUsers,long activeUsers,long totalRooms,long openRooms,long activeGameSessions,long completedGameSessions,long handsPlayed,long totalChipsInAccounts,Instant generatedAt){}
 public record UserItem(long userId,String username,String email,String role,String accountStatus,long accountChips,String displayName,Instant createdAt,int rating,long gamesRated,long totalGames,long totalHands){}
 public record RankingHistory(long gameSessionId,int oldRating,int newRating,int ratingDelta,long sessionNet,int placement,int participantCount,Instant createdAt){}
 public record UserDetail(long userId,String username,String email,String role,String accountStatus,long accountChips,String displayName,String avatarUrl,String onlineStatus,Instant createdAt,int rating,long gamesRated,int peakRating,long totalGames,long totalHands,long totalWins,long totalLosses,BigDecimal winRate,long totalChipsWon,long totalChipsLost,long netChip,long largestPotWon,long averagePlayingSeconds,List<RankingHistory> recentRankingHistory){}
 public record RoomItem(long roomId,String name,String roomType,String status,long ownerUserId,long seatedPlayers,long spectators,int capacity,Instant createdAt,Long currentGameSessionId){}
 public record RoomParticipant(long userId,String username,Integer seatNumber,String playerState,long tableChips,Instant joinedAt,Instant leftAt){}
 public record RoomDetail(long roomId,String name,String roomType,String status,long ownerUserId,String ownerUsername,int capacity,long smallBlind,long bigBlind,long buyIn,Instant createdAt,Long activeGameSessionId,List<RoomParticipant> participants){}
 public record GameItem(long gameSessionId,long roomId,String status,Instant startedAt,Instant finishedAt,long participantCount,long handCount){}
 public record GameParticipant(long userId,String username,long netChips,long handsPlayed){}
 public record GameDetail(long gameSessionId,long roomId,String roomName,String status,Instant startedAt,Instant finishedAt,long handCount,List<GameParticipant> participants){}
 public record HandItem(long handId,long handNumber,Instant startedAt,Instant endedAt,long participantCount,long totalPotAwarded,String finalPhase,String endReason,String boardCards){}
 public record MutationResponse(String status,long targetId,boolean changed,boolean deferred){}
 public record AuditItem(long id,long adminUserId,String actionType,String targetType,Long targetId,String reason,java.util.Map<String,Object> metadata,Instant createdAt){}
}
