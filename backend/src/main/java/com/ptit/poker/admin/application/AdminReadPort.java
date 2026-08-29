package com.ptit.poker.admin.application;

import static com.ptit.poker.admin.application.AdminModels.*;import java.time.Instant;

public interface AdminReadPort {
 Overview overview(Instant generatedAt);Page<UserItem> users(int page,int size,String search,String status,String role);UserDetail user(long id);
 Page<RoomItem> rooms(int page,int size,String search,String status,String roomType);RoomDetail room(long id);
 Page<GameItem> games(int page,int size,String status,Long roomId,Long userId,Instant from,Instant to);GameDetail game(long id);Page<HandItem> hands(long sessionId,int page,int size);
}
