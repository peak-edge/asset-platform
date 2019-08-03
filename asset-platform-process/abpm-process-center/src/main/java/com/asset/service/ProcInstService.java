package com.asset.service;

import com.alibaba.fastjson.JSON;
import com.asset.dao.ProcInstMapper;
import com.asset.dao.FormInstMapper;
import com.asset.dao.ProcNodeMapper;
import com.asset.dto.RegisterDTO;
import com.asset.dto.SceneSelectDTO;
import com.asset.entity.FormInstDO;
import com.asset.entity.ProcInstDO;
import com.asset.entity.ProcNodeDO;
import com.asset.exception.ProcException;
import com.asset.form.FormSheet;
import com.asset.utils.Constants;
import com.asset.utils.ProcUtils;
import com.asset.utils.JsonUtils;
import com.github.pagehelper.Page;
import com.sun.xml.internal.bind.v2.runtime.reflect.opt.Const;
import org.dom4j.*;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.io.InputStreamProvider;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.flowable.engine.*;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.ui.modeler.serviceapi.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Service
@Transactional(propagation = Propagation.REQUIRED)
public class ProcInstService {
    @Autowired
    AuthorityService authorityService;
    @Autowired
    FlowableService flowableService;
    @Autowired
    protected ModelService modelService;
    @Autowired
    FormInstService formInstService;
    @Autowired
    FormInstMapper formInstMapper;
    @Autowired
    ProcInstMapper procInstMapper;
    @Autowired
    ProcNodeMapper procNodeMapper;
    @Autowired
    FormModelService formModelService;
    @Autowired
    ProcModelService procModelService;


    public ProcessInstance getProcInst(String procInstId) {
        ProcessEngine engine = ProcessEngines.getDefaultProcessEngine();
        RepositoryService repositoryService = engine.getRepositoryService();
        RuntimeService runtimeService = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();

        return runtimeService.createProcessInstanceQuery().processInstanceId(procInstId).singleResult();
    }

    /**
     * 当前任务节点完成之后，会自动流转到下一个任务节点，数据库表中会生成这个没有被完成的任务节点的信息，
     * 里面所有数据必须都是正确的！form_inst_sheet和form_inst_value、executor、execute_time的值一开始可以为空
     * formSheet内容来自于form_model表,form_inst_value来自proc_inst表
     */
    public void saveUnCompleteTask(String procInstId,
                                   String formModelId) {
        FormSheet originalFormSheet = formModelService.getModelSheet(formModelId);
        //获取当前执行到的任务节点
        String[] taskIDs = ProcUtils.getTaskIDs(procInstId);
        //如果下面还有任务节点要处理，就在as_proc_inst中新建这个表单实例字段（每一个TaskId对应一个新的表单实例）
        for (int i = 0; i < taskIDs.length; i++) {
            boolean isNotContain = formInstMapper.getTaskId(taskIDs[i]) == null ? true : false;
            //当前要存的taskID不能是已经有的的，否则重复保存了
            if (isNotContain) {
                String formInstAllValue = getFormInstAllValue(procInstId);
                FormInstDO formInst = formInstService.createFormInst(originalFormSheet,
                        procInstId,
                        null,
                        formModelId,
                        formInstAllValue,
                        taskIDs[i]);
                String procModelId = formModelService.getProcModelID(formModelId);
                String nodeId = flowableService.getNodeId(taskIDs[i]);
                //在存进去之前，就必须要求这个节点的节点类型是确定的
                formInst.setNodeType(procModelService.getNodeType(procModelId, nodeId));
                formInstService.saveUnCompleteFormInst(formInst);
            }
        }
    }

    private String getFormInstAllValue(String procInstId) {
        return procInstMapper.getFormInstAllValue(procInstId);
    }

    public String getProcModelId(String procInstId) {
        return procInstMapper.getProcModelId(procInstId);
    }

    public String getExecutionId(String procInstID) {
        ProcessEngine engine = ProcessEngines.getDefaultProcessEngine();
        RuntimeService runtimeService = engine.getRuntimeService();

        ExecutionQuery executionQuery = runtimeService.createExecutionQuery().processInstanceId(procInstID);
        List<Execution> executions = executionQuery.list();

        Execution temp = null;
        for (int j = 0; j < executions.size(); j++) {
            temp = executions.get(j);
            if (temp.getActivityId() == null)
                continue;
            else
                break;
        }
        return temp.getId();
    }

    /**
     * 从当前审批节点回滚到上一个申请节点
     *
     * @param procInstID
     * @param rollbackActID 上一个申请节点的ActivityID
     */
    public void rollback(String procInstID, String rollbackActID) {
        String executionId = getExecutionId(procInstID);
        ProcUtils.rollback(executionId, rollbackActID, procInstID);
    }

    /**
     * 直接由流程模型ID创建相应的流程实例，在创建流程实例之前，需要根据情况添加会签标签
     *
     * @param procModelId
     * @return
     */
    public HashMap<String, Object> createProcInstance(String procModelId) throws DocumentException {
        ProcessEngine engine = ProcessEngines.getDefaultProcessEngine();
        RepositoryService repositoryService = engine.getRepositoryService();
        RuntimeService runtimeService = engine.getRuntimeService();


        org.flowable.ui.modeler.domain.Model modelData = modelService.getModel(procModelId);
        BpmnModel bpmnModel = modelService.getBpmnModel(modelData);

        //（从as_proc_node表中找）对流程模型的每一个节点进行遍历，看是否需要包含会签功能标签
        Document document = modelToDocument(bpmnModel);
        Document documentAfter = handleJointXml(procModelId, document);
        bpmnModel = documentToModel(documentAfter);


        DeploymentBuilder builder = repositoryService.createDeployment();
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(bpmnModel);


        String processXMLName = modelData.getName() + ".bpmn20.xml";
        String depResourceName = modelData.getName() + "ResName";
        String depName = modelData.getName() + "DepName";
        String depKey = modelData.getName() + "DepKey";

        //部署
        Deployment dep = builder.addBpmnModel(depResourceName, bpmnModel).
                name(depName).
                key(depKey).
                addBytes(processXMLName, bpmnBytes).   //必须加这个，否则流程定义文件会为空
                deploy();

        //获取流程定义
        String deployID = dep.getId();
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery().deploymentId(dep.getId()).singleResult();
        String defID = def.getId();
        //创建流程实例
        ProcessInstance instance = runtimeService.startProcessInstanceById(def.getId());

        HashMap<String, Object> map = new HashMap<>();
        map.put("inst", instance);
        map.put("defID", defID);
        map.put("deployID", deployID);

        return map;
    }

    /**
     * 在创建流程实例之前，（从as_proc_node表中找）需要遍历该流程模型的每一个节点，看是否需要为这个节点增加会签标签
     *
     * @param procModelId
     * @return
     */
    public Document handleJointXml(String procModelId, Document document) throws DocumentException {
        //1、先获取所有node节点
        List<ProcNodeDO> nodeDOs = procModelService.getNodeDOList(procModelId);
        for (ProcNodeDO nodeDO : nodeDOs) {
            //当前节点需要增加会签标签
            if (nodeDO.getIfJointSign() != Constants.AS_NODE_JOINT_DISABLE) {
                addJointlySign(document, nodeDO);
            }
        }
        return document;
    }

    private BpmnModel documentToModel(Document document1) {
        String str = document1.asXML();
        InputStream inputStream = new ByteArrayInputStream(str.getBytes());
        //创建转换器
        BpmnXMLConverter bpmnXMLConverter = new BpmnXMLConverter();
        InputStreamProvider inputStreamProvider = new InputStreamSource(inputStream);
        return bpmnXMLConverter.convertToBpmnModel(inputStreamProvider,
                true,
                false,
                "UTF-8");
    }

    private Document modelToDocument(BpmnModel bpmnModel) throws DocumentException {
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(bpmnModel);
        //转成string格式的xml数据
        String bytes = new String(bpmnBytes);
        //解析，添加会签功能
        return DocumentHelper.parseText(bytes);
    }


    /**
     * 根据当前流程模型数据，按需添加会签标签
     *
     * @param document
     * @param nodeDO
     * @return
     */
    public Document addJointlySign(Document document, ProcNodeDO nodeDO) throws DocumentException {
        Element definitions = document.getRootElement();
        Element process = definitions.element("process");
        List nodes = process.elements("userTask");

        Iterator iter = nodes.iterator();
        while (iter.hasNext()) {
            Element element = (Element) iter.next();
            Attribute attribute = element.attribute("id");
            if (attribute.getData().equals(nodeDO.getNodeId())) {
                Element child = element.addElement("multiInstanceLoopCharacteristics");

                if (nodeDO.getIfJointSign() == Constants.AS_NODE_JOINT_SERIAL)
                    child.addAttribute("isSequential", "true");
                else if (nodeDO.getIfJointSign() == Constants.AS_NODE_JOINT_PARRAL)
                    child.addAttribute("isSequential", "false");

                Element grandChild = child.addElement("loopCardinality");
                grandChild.addText(calculateJointNum(nodeDO));
            }
        }

        return document;
    }

    /**
     * 如果一个节点被标记为有会签功能，需要知道这个会签节点多少人进行处理之后会结束当前节点
     * 这里可以设计多种复杂规则，这里暂时根据人员（candidateUser）有多少个，就设置该循环值是多少
     *
     * @return
     */
    private String calculateJointNum(ProcNodeDO nodeDO) {
        String candidateusers = nodeDO.getCandidateUser();
        String[] users = candidateusers.split("\\|");
        return String.valueOf(users.length);
    }

    /**
     * 这里是从本地BPMN文件读取
     * 发起一个注册流程，注册流程表单、绑定的流程模型、绑定的权限数据、流程模型中节点类型都需要事先创建好！！
     *
     * @param dto
     * @throws ProcException
     */
    public String[] createRegisterProcByXml(RegisterDTO dto) throws ProcException {
        ProcessInstance procInst = ProcUtils.createProcInstByXml(Constants.REGISTER_BPMN_NAME);
        if (procInst == null) {
            throw new ProcException("无法创建流程实例，请查看本地BPMN资源文件");
        }
        String[] taskIDs = ProcUtils.getTaskIDs(procInst.getProcessInstanceId());
        //3、持久化流程实例
        ProcInstDO asProcInstDO = new ProcInstDO(
                procInst.getProcessInstanceId(),
                Constants.REGISTER_PROC_ID,
                "",
                "",
                dto.getEditor(),
                dto.getForm_inst_value());
        insertProcInst(asProcInstDO);
        ProcUtils.completeTask(taskIDs[0]);
        saveUnCompleteTask(
                procInst.getProcessInstanceId(),
                Constants.REGISTER_FORM_ID
        );
        //生成一个或多个外链，当前待办的任务节点的执行人会受到这个URL，执行人点击这个URL就会跳转到相应的页面进行登录
        String[] urls = formInstService.generateUrls(procInst);
        return urls;
    }

    public String[] createSceneSelectProcByXml(SceneSelectDTO dto) {
        ProcessInstance procInst = ProcUtils.createProcInstByXml(Constants.SCENE_SELECT_BPMN_NAME);
        if (procInst == null) {
            throw new ProcException("无法创建流程实例，请查看本地BPMN资源文件");
        }
        String[] taskIDs = ProcUtils.getTaskIDs(procInst.getProcessInstanceId());
        //3、持久化流程实例
        ProcInstDO asProcInstDO = new ProcInstDO(
                procInst.getProcessInstanceId(),
                Constants.SCENE_SELECT_PROC_ID,
                "",
                "",
                dto.getEditor(),
                dto.getForm_inst_value());
        insertProcInst(asProcInstDO);
        ProcUtils.completeTask(taskIDs[0]);
        saveUnCompleteTask(
                procInst.getProcessInstanceId(),
                Constants.SCENE_SELECT_PROC_ID
        );
        //生成一个或多个外链，当前待办的任务节点的执行人会受到这个URL，执行人点击这个URL就会跳转到相应的页面进行登录
        String[] urls = formInstService.generateUrls(procInst);
        return urls;
    }

    public void insertProcInst(ProcInstDO procInstDO) {
        procInstMapper.insert(procInstDO);
    }

    /**
     * 具体的逻辑 是取出这一项，转换成实体类，与提交的form_inst_value（也被转换为实体类）作对比，把没有的内容加到实体类上，最后转成str，更新as_proc_inst中的form_inst_all_value值
     *
     * @param formInstValueStr
     */
    public void updateFormValueForAll(String formInstValueStr, String procInstId) {

        String originalFormValue = getFormInstAllValue(procInstId);
        HashMap<String, Object> originalMap = JsonUtils.constructHashMap(originalFormValue);

        HashMap<String, Object> commitMap = JsonUtils.constructHashMap(formInstValueStr);
        originalMap.putAll(commitMap);

        String newOriginalValue = JSON.toJSONString(originalMap);

        procInstMapper.updateFormValueForAll(procInstId, newOriginalValue);
    }

    /**
     * 返回一个任务节点所处的流程实例是谁发起的
     * @param taskId
     * @return
     */
    public String getCommitter(String taskId) {
        String procInstId = formInstService.getProcInstId(taskId);
        return procInstMapper.getCommitter(procInstId);
    }

    public String getDefId(String curTaskId) {
        return procInstMapper.getDefId(curTaskId);
    }

    public void deleteProcInstDO(String procInstId) {
        procInstMapper.deleteByPrimaryKey(procInstId);
    }

    public List<ProcInstDO> listProcInsts() {
        return procInstMapper.listProcInsts();
    }


}
