package com.asset.controller;

import com.asset.base.BaseController;
import com.asset.entity.User;
import com.asset.exception.ProcException;
import com.asset.javabean.RespBean;
import com.asset.dto.RegisterDTO;
import com.asset.service.FormInstService;
import com.asset.service.ProcInstService;
import com.asset.service.UserService;
import com.asset.utils.Constants;
import com.asset.utils.PageGrids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * @author hjhu
 */
@RestController
public class UserController extends BaseController {

    private final static Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    @Autowired
    ProcInstService procInstService;
    @Autowired
    UserService userService;
    @Autowired
    FormInstService formInstService;

    /**
     * 用户管理页面
     * @return
     */
    @RequestMapping("/userList")
    public String procDefIndex() {
        return "pages/identity/user_list";
    }

    /**
     * 查询用户数据
     * @param pageNum
     * @param pageSize
     * @return PageGrids
     */
    @RequestMapping("/queryUsers")
    @ResponseBody
    public PageGrids queryUsers(@RequestParam("page") Integer pageNum,
                                @RequestParam("rows") Integer pageSize,
                                @RequestParam String id,
                                @RequestParam String displayName) {

        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = Constants.PageSize;
        }
        PageGrids pageGrids = userService.getUsers(pageNum, pageSize, id, displayName);
        return pageGrids;
    }

    /**
     * 添加用户
     *
     * @param displayName
     * @return
     */
    @PostMapping("/addUser")
    @ResponseBody
    public String addUser(@RequestParam String displayName,
                          @RequestParam String phoneNumber,
                          @RequestParam String email) {
        User user = new User();
        user.setId("2");
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setAdmin(true);
        user.setStatus(true);
        user.setPwd("123456");
        user.setPhoneNumber(phoneNumber);
        user.setCreatedTime(new Date());
        int flag = userService.addUser(user);
        if (flag > 0) {
            return "pages/identity/user_list";
        } else {
            return "index";
        }
    }

    /**
     * 前端发起注册，生成一个固定表单样式，固定的表单样式以及对应的审批流程模型应该在数据库创建时就存在数据库表中
     *
     * @param dto
     * @return
     */
    @RequestMapping(value = "/form_inst/register/save", method = RequestMethod.POST)
    public RespBean register(@RequestBody RegisterDTO dto) {
        String[] urls = null;
        try {
            urls = procInstService.createRegisterProcByXml(dto);
        } catch (ProcException procException) {
            LOGGER.error(procException.getMessage());
            return RespBean.error("创建注册流程出错！");
        }

        return RespBean.ok("",urls);
    }



}
