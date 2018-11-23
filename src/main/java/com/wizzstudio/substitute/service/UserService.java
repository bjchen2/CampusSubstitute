package com.wizzstudio.substitute.service;

import com.wizzstudio.substitute.dto.ModifyUserInfoDTO;
import com.wizzstudio.substitute.domain.User;
import java.math.BigDecimal;


/**
 * 定义了用户相关的基本方法
 */
public interface UserService {
    /**
     *
     * @param user 用户信息
     * @return
     */
    User saveUser(User user);

    /**
     * 通过openId获取用户信息
     * @param openid 用户openid
     * @return
     */
    User getByOpenid(String openid);

    /**
     * 更新用户信息
     * @param newInfo 可更新的用户信息
     */
    void modifyUserInfo(String id, ModifyUserInfoDTO newInfo);

    /**
     * 添加推荐人
     * @param userId 用户id
     * @param masterId 师傅id
     * @return true 添加成功, false 添加失败
     */
    boolean addReferrer(String userId, String masterId);

    /**
     * 获取用户所有徒弟的基本信息
     * @param userId 徒弟id
     * @param type 指定返回类型
     * @return 用户的基本信息
     */
    <T>T getBasicInfo(T type, String userId);

    User findUserByOpenId(String openid);

    User findUserById(String id);

    /**
     * 扣除某用户number的余额量
     * @param userId 用户id
     * @param number 减少金额量
     */
    void reduceBalance(String userId, BigDecimal number);


}
