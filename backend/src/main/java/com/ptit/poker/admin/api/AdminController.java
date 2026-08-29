package com.ptit.poker.admin.api;

import com.ptit.poker.admin.application.AdminQueryService;import static com.ptit.poker.admin.application.AdminModels.*;import java.time.Instant;import org.springframework.context.annotation.Profile;import org.springframework.format.annotation.DateTimeFormat;import org.springframework.web.bind.annotation.*;

@RestController @Profile("!bootstrap") @RequestMapping("/api/v1/admin")
public class AdminController {private final AdminQueryService admin;public AdminController(AdminQueryService admin){this.admin=admin;}
 @GetMapping("/overview") public Overview overview(){return admin.overview();}
 @GetMapping("/users") public Page<UserItem> users(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)String status,@RequestParam(required=false)String role){return admin.users(page,size,search,status,role);}
 @GetMapping("/users/{id}") public UserDetail user(@PathVariable long id){return admin.user(id);}
 @GetMapping("/rooms") public Page<RoomItem> rooms(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)String status,@RequestParam(required=false)String roomType){return admin.rooms(page,size,search,status,roomType);}
 @GetMapping("/rooms/{id}") public RoomDetail room(@PathVariable long id){return admin.room(id);}
 @GetMapping("/games") public Page<GameItem> games(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status,@RequestParam(required=false)Long roomId,@RequestParam(required=false)Long userId,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to){return admin.games(page,size,status,roomId,userId,from,to);}
 @GetMapping("/games/{id}") public GameDetail game(@PathVariable long id){return admin.game(id);}
 @GetMapping("/games/{id}/hands") public Page<HandItem> hands(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return admin.hands(id,page,size);}
}
