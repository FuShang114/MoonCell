package com.mooncell.gateway.core.dao;

import com.mooncell.gateway.core.task.ChatTask;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TaskMapper {

    @Insert("INSERT INTO chat_task (id, idempotency_key, model, request_json, status, created_at) VALUES (#{id}, #{idempotencyKey}, #{requestJson}, #{status}, CURRENT_TIMESTAMP)")
    void insert(ChatTask task);

    // CAS 更新：只有当前状态匹配时才更新
    @Update("UPDATE chat_task SET status = #{newStatus}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = #{expectStatus}")
    int compareAndSetStatus(@Param("id") String id, @Param("expectStatus") String expectStatus, @Param("newStatus") String newStatus);

    // 幂等查询
    @Select("SELECT * FROM chat_task WHERE idempotency_key = #{key}")
    ChatTask findByIdempotencyKey(String key);

    @Select("SELECT * FROM chat_task WHERE status = 'PENDING'")
    List<ChatTask> findPendingTasks();
}
