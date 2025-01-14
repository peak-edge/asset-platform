package com.asset.bean;

import io.swagger.annotations.ApiModel;

@ApiModel(value = "组织场景关联表")
public class OrganScene {

    private Long id;
    private String nodeId;
    private String parentId;
    private String sceneId;
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId == null ? null : nodeId.trim();
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId == null ? null : parentId.trim();
    }

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId == null ? null : sceneId.trim();
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
