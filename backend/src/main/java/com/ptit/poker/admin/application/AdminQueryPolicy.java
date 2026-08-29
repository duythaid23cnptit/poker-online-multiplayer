package com.ptit.poker.admin.application;

import java.time.*;import java.util.Set;import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;

public final class AdminQueryPolicy {private AdminQueryPolicy(){}
 public static void pagination(int page,int size){if(page<0||size<1||size>100)bad("page must be non-negative and size between 1 and 100");}
 public static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
 public static String allowed(String value,Set<String> allowed){String normalized=optional(value);if(normalized==null)return null;normalized=normalized.toUpperCase();if(!allowed.contains(normalized))bad("unsupported filter value");return normalized;}
 public static void dates(Instant from,Instant to){if(from!=null&&to!=null&&(from.isAfter(to)||Duration.between(from,to).toDays()>366))bad("invalid admin date range");}
 private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
