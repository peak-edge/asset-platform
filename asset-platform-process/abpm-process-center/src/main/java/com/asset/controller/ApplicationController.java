package com.asset.controller;


import com.asset.entity.Application;
import com.asset.entity.AsFormModel;
import com.asset.javabean.RespBean;
import com.asset.service.ApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用管理控制器
 */
@RestController
@RequestMapping("/application")
public class ApplicationController {

    final private static Logger LOGGER = LoggerFactory.getLogger(ApplicationController.class);

    @Autowired
    private ApplicationService applicationService;



    @RequestMapping(value = "/addApp", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ApiOperation(value = "添加应用", notes = "应用添加",tags = "应用", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "applicationName", value = "应用名称", required = true, dataType = "String"),
            @ApiImplicitParam(name = "iconCls", value = "应用图标", required = true, dataType = "String")
    })
    @ApiResponses({
            @ApiResponse(code = 200,message = "新建成功",response = RespBean.class),
            @ApiResponse(code = 500,message = "系统错误",response = RespBean.class)
    })
    public RespBean addApp(@RequestBody Application application){
        int flag = applicationService.addApplication(application);
        if (flag < 0){
            LOGGER.info("应用新建失败");
            return RespBean.error("新建失败");
        }
        return RespBean.ok("新建成功","");
    }

    @RequestMapping(value = "/updateApp", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    @ApiOperation(value = "修改应用", notes = "应用修改",tags = "应用", httpMethod = "PUT")
    @ApiResponses({
            @ApiResponse(code = 200,message = "修改成功",response = RespBean.class),
            @ApiResponse(code = 500,message = "系统错误",response = RespBean.class)
    })
    public RespBean updateApp(@RequestBody Application application){
        LOGGER.info(application.toString());
        Application oldApp = applicationService.getById(application.getId());
        if(null == oldApp){
            return RespBean.error("数据错误");
        }
        application.setStatus(oldApp.getStatus());
        application.setCreatedTime(oldApp.getCreatedTime());
        application.setIsPublished(oldApp.getIsPublished());
        int flag = applicationService.updateApplication(application);
        if(flag < 0) {
            return RespBean.error("修改失败");
        } else {
            return RespBean.ok("修改成功");
        }
    }

    @RequestMapping(value = "/deleteApp", method = RequestMethod.DELETE, produces = "application/json;charset=UTF-8")
    @ApiOperation(value = "删除应用", notes = "应用删除",tags = "应用", httpMethod = "DELETE")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "应用id", required = true, dataType = "String"),
    })
    @ApiResponses({
            @ApiResponse(code = 200,message = "删除成功",response = RespBean.class),
            @ApiResponse(code = 500,message = "系统错误",response = RespBean.class)
    })
    public RespBean deleteApp(@RequestBody Application application){
        Application oldApp = applicationService.getById(application.getId());
        if(null == oldApp){
            return RespBean.error("数据错误");
        }
        oldApp.setStatus(0);
        int flag = applicationService.updateApplication(oldApp);
        if(flag < 0) {
            return RespBean.error("删除失败");
        } else {
            return RespBean.ok("删除成功");
        }
    }

    @RequestMapping(value = "/appList", method = RequestMethod.GET)
    @ApiOperation(value = "获取应用列表", tags = "应用", httpMethod = "GET")
    @ApiResponse(code = 200, message = "", response = List.class)
    public List<Application> getAppList(){
        return applicationService.getAppList();
    }



    /**
     * 根据传入的AppID获取该应用下所有表单模型
     */
    @RequestMapping(value = "/app/getModels", method = RequestMethod.GET)
    public RespBean getFormModels(@RequestParam(value = "app_id") String appID) throws JsonProcessingException {
        ArrayList<AsFormModel> asFormModels = (ArrayList<AsFormModel>) applicationService.getFormModels(appID);

        return RespBean.ok("",asFormModels);
    }
}