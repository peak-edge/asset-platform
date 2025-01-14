package com.asset.mapper;

import com.asset.bean.OrganTree;

public interface OrganTreeMapper {
    int deleteByPrimaryKey(String id);

    int insert(OrganTree record);

    int insertSelective(OrganTree record);

    OrganTree selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(OrganTree record);

    int updateByPrimaryKey(OrganTree record);
}