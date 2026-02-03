package com.mooncell.gateway.core.dao;

import com.mooncell.gateway.core.model.ModelInstance;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ModelInstanceMapper {

    @Select("""
        SELECT m.*, p.name as provider_name 
        FROM model_instance m 
        LEFT JOIN provider p ON m.provider_id = p.id
        WHERE m.model_name = #{modelName}
    """)
    List<ModelInstance> findByModelName(String modelName);

    @Select("""
        SELECT m.*, p.name as provider_name 
        FROM model_instance m 
        LEFT JOIN provider p ON m.provider_id = p.id
    """)
    List<ModelInstance> findAll();

    @Select("SELECT * FROM model_instance WHERE url = #{url}")
    ModelInstance findByUrl(String url);

    @Insert("""
        INSERT INTO model_instance (provider_id, model_name, url, api_key, weight, is_active)
        VALUES (#{providerId}, #{modelName}, #{url}, #{apiKey}, #{weight}, #{isActive})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelInstance instance);

    @Update("UPDATE model_instance SET is_active = #{isActive} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("isActive") Boolean isActive);
    
    @Update("UPDATE model_instance SET weight = #{weight} WHERE id = #{id}")
    int updateWeight(@Param("id") Long id, @Param("weight") Integer weight);

    @Select("SELECT id FROM provider WHERE name = #{name}")
    Long findProviderIdByName(String name);
    
    @Insert("INSERT INTO provider (name, description) VALUES (#{name}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProvider(@Param("name") String name, @Param("description") String description);
}

