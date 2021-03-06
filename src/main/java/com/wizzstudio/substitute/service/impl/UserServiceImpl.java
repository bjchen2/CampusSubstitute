package com.wizzstudio.substitute.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaUserInfo;
import com.wizzstudio.substitute.constants.Constant;
import com.wizzstudio.substitute.dao.CouponInfoDao;
import com.wizzstudio.substitute.dao.SchoolDao;
import com.wizzstudio.substitute.dao.UserDao;
import com.wizzstudio.substitute.domain.CouponInfo;
import com.wizzstudio.substitute.dto.CheckCodeDto;
import com.wizzstudio.substitute.dto.UserBasicInfo;
import com.wizzstudio.substitute.dto.ModifyUserInfoDTO;
import com.wizzstudio.substitute.dto.wx.WxInfo;
import com.wizzstudio.substitute.enums.GenderEnum;
import com.wizzstudio.substitute.domain.User;
import com.wizzstudio.substitute.enums.Role;
import com.wizzstudio.substitute.exception.SubstituteException;
import com.wizzstudio.substitute.service.SchoolService;
import com.wizzstudio.substitute.service.UserService;
import com.wizzstudio.substitute.util.RandomUtil;
import com.wizzstudio.substitute.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import javax.persistence.Query;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl extends BaseService implements UserService {

    @Autowired
    UserDao userDao;
    @Autowired
    SchoolService schoolService;
    @Autowired
    WxMaService wxService;
    @Autowired
    SchoolDao schoolDao;
    @Autowired
    CouponInfoDao couponInfoDao;
    @Autowired
    RedisUtil redisUtil;


    @Override
    public User userLogin(WxInfo loginData) throws WxErrorException {
        //通过code获取openid
        WxMaJscode2SessionResult sessionResult = wxService.getUserService().getSessionInfo(loginData.getCode());
        log.info("[用户登录]loginData={},sessionResult={}", loginData, sessionResult);
        //通过openid在数据库中寻找是否存在该用户
        User user = findUserByOpenId(sessionResult.getOpenid());
        //若不存在，则注册用户
        if (user == null) {
            //获得一个未被使用过的userId
            String userId = RandomUtil.getSixRandom();
            user = findUserById(userId);
            while (user != null) {
                userId = RandomUtil.getSixRandom();
                user = findUserById(userId);
            }
            //获取用户信息
            WxMaUserInfo wxUserInfo = wxService.getUserService().getUserInfo(sessionResult.getSessionKey(), loginData.getEncryptedData(), loginData.getIv());
            //构造用户信息
            user = User.newBuilder()
                    .setId(userId)
                    .setUserName(wxUserInfo.getNickName())
                    .setOpenid(wxUserInfo.getOpenId())
                    .setAvatar(wxUserInfo.getAvatarUrl())
                    .build();
            switch (Integer.valueOf(wxUserInfo.getGender())) {
                //性别 0：未知、1：男、2：女
                case 0:
                    user.setGender(GenderEnum.NO_LIMITED);
                    break;
                case 1:
                    user.setGender(GenderEnum.MALE);
                    break;
                case 2:
                    user.setGender(GenderEnum.FEMALE);
                    break;
                default:
                    log.error("[用户注册]注册失败，用户信息有误，sex={}", wxUserInfo.getGender());
                    throw new SubstituteException("用户信息有误");
            }
            log.info("[用户注册]注册新用户成功，openid={} " + user.getOpenid());
            //保存用户信息到数据库
            return saveUser(user);
        }
        //若用户已存在，直接返回用户信息
        return user;
    }

    @Override
    public User saveUser(User user) {
        return userDao.save(user);
    }

    @Override
    public void modifyUserInfo(String id, ModifyUserInfoDTO newInfo) {
        //校验短信验证码是否有效 --cx
        CheckCodeDto checkCodeDto = (CheckCodeDto) redisUtil.getObj(Constant.CHECK_CODE.concat(id), CheckCodeDto.class);
        if (checkCodeDto == null || !checkCodeDto.getCode().equals(newInfo.getCheckCode())){
            log.error("验证码校验失败,id{},newInfo={}", id,newInfo);
            throw new SubstituteException("验证码校验失败");
        }
        //更改电话号码为验证码对应的电话号码，防止用户通过旧验证码修改新号码
        newInfo.setPhone(checkCodeDto.getPhone());

        User user = findUserById(id);
        BeanUtils.copyProperties(newInfo, user);
        userDao.save(user);
    }

    @Override
    public boolean addReferrer(String userId, String masterId) {
        User master = findUserById(masterId);
        User user = findUserById(userId);
        if (master != null && user != null) {
            user.setMasterId(masterId);
            userDao.save(user);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取徒弟或师傅的基本信息
     *
     * @param returnType 传入一个实例指定返回类型，list作为徒弟处理，UserBasicInfo作为师傅处理
     * @param userId     徒弟id
     * @return 返回一个list或者UserBasicInfo
     */
    @Override
    @SuppressWarnings(value = "unchecked")
    public <T> T getBasicInfo(T returnType, String userId) {
        User user = findUserById(userId);
        if (user == null) return null;
        if (returnType instanceof List) {
            Query query = entityManager.createNamedQuery
                    ("getAllApprentice", User.class).setParameter("account", userId);
            List<User> apprentices = (List<User>) query.getResultList();
            List<UserBasicInfo> basicInfoList = new ArrayList<>();
            apprentices.forEach(x -> {
                UserBasicInfo basicInfo = new UserBasicInfo();
                BeanUtils.copyProperties(x, basicInfo);
                basicInfo.setSchoolName(schoolDao.findSchoolById(x.getSchoolId()).getSchoolName());
                basicInfoList.add(basicInfo);
            });
            return (T) basicInfoList;
        } else if (returnType instanceof UserBasicInfo) {
            String masterId = user.getMasterId();
            if (masterId != null) {
                User master = findUserById(masterId);
                UserBasicInfo basicInfo = new UserBasicInfo();
                BeanUtils.copyProperties(master, basicInfo);
                basicInfo.setSchoolName(schoolDao.findSchoolById(master.getSchoolId()).getSchoolName());
                return (T) basicInfo;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public User findUserByOpenId(String openid) {
        return userDao.findByOpenid(openid);
    }

    @Override
    //@Cacheable(cacheNames = "user", key = "#id", unless = "#result==null")
    public User findUserById(String id) {
        return userDao.findUserById(id);
    }

    @Override
    public void reduceBalance(String userId, BigDecimal number) {
        User user = findUserById(userId);
        if (user.getBalance().compareTo(number) < 0) {
            log.error("【用户支付】支付失败，用户余额不足，balance={},reduceNumber={}", user.getBalance(), number);
            throw new SecurityException("支付失败，用户余额不足");
        }
        //扣钱并保存信息
        user.setBalance(user.getBalance().subtract(number));
        saveUser(user);
    }

    @Override
    public String getMasterTodayIncome(String userId) {
        String ans = redisUtil.get(Constant.MASTER_TODAY_INCOME.concat(userId));
        return ans == null ? "0" : ans;
    }


}
