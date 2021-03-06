package com.wizzstudio.substitute.enums.indent;

import com.wizzstudio.substitute.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单加急类型
 * Created By Cx On 2018/11/19 23:40
 */
@Getter
@AllArgsConstructor
public enum UrgentTypeEnum implements CodeEnum {
    NOT_URGENT(0, "未加急"),
    OVERTIME(1, "超时"),
    CANCEL(2, "退单"),
    ;

    //状态码
    private Integer code;
    //状态描述
    private String message;
}
