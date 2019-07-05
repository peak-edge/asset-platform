package com.asset.rec;

import com.asset.javabean.FormJson;
import lombok.Data;

/**
 * 经办节点
 */
@Data
public class FormInstRecHandle extends FormInstRecBase {
    //待办节点的表单实例ID
    String form_inst_id;
    String task_id;
    String proc_inst_id;
    FormJson form_inst_json;
}