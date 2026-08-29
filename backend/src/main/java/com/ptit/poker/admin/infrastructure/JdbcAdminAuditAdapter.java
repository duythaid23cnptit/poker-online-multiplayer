package com.ptit.poker.admin.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.admin.application.*;
import com.ptit.poker.admin.application.AdminModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!bootstrap")
public class JdbcAdminAuditAdapter implements AdminAuditPort {
    private static final TypeReference<Map<String,Object>> MAP = new TypeReference<>() {};
    private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper json;
    public JdbcAdminAuditAdapter(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Override public void record(long adminUserId,AdminActionType actionType,AdminTargetType targetType,Long targetId,
                                 String reason,String requestId,Map<String,Object> metadata){
        try {
            jdbc.update("INSERT INTO admin_audit_log(admin_user_id,action_type,target_type,target_id,reason,request_id,metadata_json,created_at) VALUES(:admin,:action,:target,:targetId,:reason,:requestId,:metadata,NOW(6))",
                    new MapSqlParameterSource().addValue("admin",adminUserId).addValue("action",actionType.name())
                            .addValue("target",targetType.name()).addValue("targetId",targetId).addValue("reason",reason)
                            .addValue("requestId",requestId).addValue("metadata",json.writeValueAsString(metadata)));
        } catch(JsonProcessingException ex){throw new IllegalStateException("Admin audit metadata serialization failed",ex);}
    }

    @Override public Page<AuditItem> find(int page,int size,Long adminId,AdminActionType action,AdminTargetType target,
                                          Long targetId,Instant from,Instant to){
        StringBuilder where=new StringBuilder(" WHERE 1=1"); MapSqlParameterSource p=new MapSqlParameterSource();
        append(where,p,"admin_user_id","admin",adminId); append(where,p,"action_type","action",action==null?null:action.name());
        append(where,p,"target_type","target",target==null?null:target.name()); append(where,p,"target_id","targetId",targetId);
        if(from!=null){where.append(" AND created_at>=:from");p.addValue("from",LocalDateTime.ofInstant(from,ZoneOffset.UTC));}
        if(to!=null){where.append(" AND created_at<:to");p.addValue("to",LocalDateTime.ofInstant(to,ZoneOffset.UTC));}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log"+where,p,Long.class);
        p.addValue("limit",size).addValue("offset",Math.multiplyExact(page,size));
        List<AuditItem> items=jdbc.query("SELECT id,admin_user_id,action_type,target_type,target_id,reason,metadata_json,created_at FROM admin_audit_log"+where+" ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",p,this::map);
        return new Page<>(items,page,size,total);
    }
    private AuditItem map(ResultSet rs,int row)throws SQLException{
        Long targetId=rs.getObject("target_id",Long.class); String raw=rs.getString("metadata_json");
        try{return new AuditItem(rs.getLong("id"),rs.getLong("admin_user_id"),rs.getString("action_type"),rs.getString("target_type"),targetId,rs.getString("reason"),raw==null?Map.of():json.readValue(raw,MAP),rs.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC));}
        catch(JsonProcessingException ex){throw new SQLException("Invalid admin audit metadata",ex);}
    }
    private static void append(StringBuilder sql,MapSqlParameterSource p,String column,String name,Object value){if(value!=null){sql.append(" AND ").append(column).append("=:").append(name);p.addValue(name,value);}}
}
