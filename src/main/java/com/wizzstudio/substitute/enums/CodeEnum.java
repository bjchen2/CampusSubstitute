package com.wizzstudio.substitute.enums;

/**
 * 用于其他枚举类实现，保证有getCode方法，使实现类能够通过EnumUtil类遍历查询code值符合要求的枚举
 * Created By Cx On 2018/11/19 23:40
 */
public interface CodeEnum {
    Integer getCode();
}
