package com.wizzstudio.substitute.service;

import com.wizzstudio.substitute.domain.Indent;
import com.wizzstudio.substitute.enums.indent.UrgentTypeEnum;

/**
 * 发送模板消息
 * Created By Cx On 2018/11/29 14:46
 */
public interface PushMessageService {

    /**
     * 发送小程序模板消息给用户: 使用WxMaService封装好的轮子
     */
    void sendTemplateMsg(Indent indent, String formId);

    /**
     * 发送短信给下单人
     * @param userId     用户id
     * @param urgentCode  与UrgentTypeEnum一一对应
     */
    void sendPhoneMsg(String userId, UrgentTypeEnum urgentType);
}
