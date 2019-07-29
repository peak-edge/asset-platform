package com.asset.controller;

import com.asset.bean.*;
import com.asset.common.SystemConstant;
import com.asset.service.*;
import com.asset.utils.Func;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("api")
public class ApiController {

    @Autowired
    private ISceneService sceneService;

    @Autowired
    private IResourceService resourceService;

    @Autowired
    private IOrganService organService;

    @Autowired
    IUserService userService;

    @Autowired
    private ISceneRoleService sceneRoleService;

    @Autowired
    private IUserRoleService userRoleService;

    /*@ApiOperation(value = "获取所有场景信息", notes = "获取所有场景信息",tags = "组织", httpMethod = "GET")
    @RequestMapping(value = "rest/scenes", method = RequestMethod.GET)
    public RespBean getAllScene(@ApiParam(value = "page", defaultValue = "1",required = true) @RequestParam(defaultValue = "1") Integer page
            , @ApiParam(value = "size", defaultValue = "1",required = true) @RequestParam(defaultValue = "20") Integer size){
        List<Scene> scenes = sceneService.getAllScenesByPage(page, size);
        if (Func.isNull(scenes)){
            return RespBean.error(SystemConstant.SYSTEM_FAILURE);
        }
        return RespBean.ok(SystemConstant.GET_SUCCESS, scenes);
    }*/

    /**
     * 分页获取场景信息
     * @param page
     * @param size
     * @return RespBean
     */
    @ApiOperation(value = "获取所有场景信息", notes = "获取所有场景信息;page为起始页;size为每页显示数量"
            , tags = "开放api", httpMethod = "GET")
    @RequestMapping(value = "rest/scenes", method = RequestMethod.GET)
    public RespBean getAllSceneByPage(@ApiParam(value = "page", defaultValue = "1", required = true) @RequestParam(defaultValue = "1") Integer page
            , @ApiParam(value = "size", defaultValue = "1", required = true) @RequestParam(defaultValue = "10") Integer size){
        PageHelper.startPage(page, size);
        List<Scene> scenes = sceneService.getAllScene();
        if (Func.isNull(scenes)){
            return RespBean.error(SystemConstant.SYSTEM_FAILURE);
        }
        PageInfo<Scene> scenePageInfo = new PageInfo<>(scenes);
        return RespBean.ok(SystemConstant.GET_SUCCESS, scenePageInfo);
    }

    /**
     * 获取主组织树（需为管理员角色）
     * @return RespBean
     */
    @ApiOperation(value = "获取主组织树", notes = "已完成",tags = "开放api", httpMethod = "GET")
    @RequestMapping(value = "/mainTree", method = RequestMethod.GET)
    public RespBean getOrganMainTree(){
        return RespBean.data(organService.getMainTree());
    }

    @ApiOperation(value = "添加菜单（表单类型）", notes = "传FormModelInfo实体与sceneId(param);",tags = "菜单", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "applicationId", value = "应用id", required = true, dataType = "String"),
            @ApiImplicitParam(name = "formModelId", value = "表单id", required = true, dataType = "String"),
            @ApiImplicitParam(name = "formName", value = "表单名称", required = true, dataType = "String"),
            @ApiImplicitParam(name = "iconCls", value = "表单图标", required = true, dataType = "String"),
            @ApiImplicitParam(name = "groupId", value = "表单分组id", required = true, dataType = "String"),
            @ApiImplicitParam(name = "groupName", value = "表单分组名称", required = true, dataType = "String"),
            @ApiImplicitParam(name = "sceneId", value = "场景id", required = true, dataType = "String")
    })
    @PostMapping(value = "form/add")
    @Transactional
    public RespBean addResource(@RequestBody FormModelInfo formModelInfo, @RequestParam("sceneId") String sceneId) throws CloneNotSupportedException {
        if (Func.hasEmpty(formModelInfo.getApplicationId(), formModelInfo.getFormModelId(), formModelInfo.getFormName()
                , formModelInfo.getIconCls(), formModelInfo.getGroupId(), formModelInfo.getGroupName(), sceneId)){
            return RespBean.error("参数错误");
        }
        Resource parentResource = resourceService.getResourceByPath(formModelInfo.getApplicationId());
        if (Func.isNull(parentResource)){
            return RespBean.error("应用不存在");
        }
        if(resourceService.formExists(formModelInfo.getFormName(), sceneId, parentResource.getId())){
            return RespBean.error("表单名称已被使用，请更换后再试");
        }
        Resource resource = new Resource();
        //1-应用；2-表单；3-表单操作
        resource.setCategory(2);
        resource.setParentId(parentResource.getId());
        resource.setAddTime(new Date());
        resource.setIsDeleted(0);
        resource.setSort(0);
        resource.setLevel(0);
        resource.setPath(formModelInfo.getFormModelId());
        resource.setName(formModelInfo.getFormName());
        resource.setIconCls(formModelInfo.getIconCls());
        resource.setCode(SystemConstant.CODE_FORM);
        // 为表单设置分组
        resource.setGroupId(formModelInfo.getGroupId());
        resource.setGroupName(formModelInfo.getGroupName());
        //为表单设置场景
        resource.setSceneId(sceneId);
        resourceService.save(resource);
        resourceService.addResource4Admin(resource);
        resourceService.addFuncResource(resource);
        return RespBean.ok(SystemConstant.ADD_SUCCESS);
    }

    @ApiOperation(value = "注册用户激活", notes = "（已完成）sceneId场景ID;userId用户ID;组织管理员审核时sceneId置null或\"\"",tags = "用户", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(value = "sceneId", required = true, name = "场景id", dataTypeClass = String.class),
            @ApiImplicitParam(value = "userId", required = true, name = "用户id", dataTypeClass = String.class),
            @ApiImplicitParam(value = "auditType", required = true, name = "审核类型:1-组织审核；2-平台审核", dataTypeClass = Integer.class)
    })
    @PostMapping(value = "active")
    @Transactional
    public RespBean userActivate(@RequestParam String sceneId, @RequestParam String userId, @RequestParam Integer auditType) {
        if (userService.getById(userId).getAdmin() == 1){
            //TODO:
            return RespBean.error("注册为平台管理员的接口还没做");
        }
        if (auditType == 2){
            //场景的默认角色有效化
            sceneRoleService.roleAvailable(sceneId);
        }
        userService.enableUser(userId);
        //设置平台级权限
        UserRole userRole = new UserRole();
        userRole.setCreatedTime(new Date());
        userRole.setUid(userId);
        userRole.setStatus(1);
        userRole.setRoleId(SystemConstant.SYSTEM_DEFAULT_USER);
        userRoleService.save(userRole);
        sceneService.enableScene(userId, sceneId);
        return RespBean.ok("用户审核通过");
    }
}
