package com.wizzstudio.substitute.service.impl;

import com.wizzstudio.substitute.VO.IndentVO;
import com.wizzstudio.substitute.dao.IndentDao;
import com.wizzstudio.substitute.domain.Address;
import com.wizzstudio.substitute.domain.School;
import com.wizzstudio.substitute.domain.User;
import com.wizzstudio.substitute.enums.GenderEnum;
import com.wizzstudio.substitute.enums.indent.IndentSortTypeEnum;
import com.wizzstudio.substitute.enums.indent.IndentStateEnum;
import com.wizzstudio.substitute.domain.Indent;
import com.wizzstudio.substitute.exception.CheckException;
import com.wizzstudio.substitute.exception.SubstituteException;
import com.wizzstudio.substitute.service.AddressService;
import com.wizzstudio.substitute.service.IndentService;
import com.wizzstudio.substitute.service.SchoolService;
import com.wizzstudio.substitute.service.UserService;
import com.wizzstudio.substitute.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class IndentServiceImpl implements IndentService {

    @Autowired
    IndentDao indentDao;
    @Autowired
    UserService userService;
    @Autowired
    AddressService addressService;
    @Autowired
    SchoolService schoolService;

    /**
     * 将indent 封装为 indentVO
     */
    private IndentVO indent2VO(Indent indent) throws CheckException {
        IndentVO indentVO = new IndentVO();
        BeanUtils.copyProperties(indent,indentVO);
        Address shpping = addressService.getById(indent.getShippingAddressId()),
                takeGoods = addressService.getById(indent.getTakeGoodAddressId());
        //检验地址信息是否有效,并进行拼装
        if (shpping == null){
            log.warn("[获取订单信息]shippingAddressId不存在，indent={}", indent);
            indentVO.setShippingAddress(null);
        }else {
            indentVO.setShippingAddress(shpping.getAddress());
        }
        if (takeGoods == null){
            log.warn("[获取订单信息]shippingAddressId不存在，indent={}", indent);
            indentVO.setTakeGoodAddress(null);
        }else {
            indentVO.setTakeGoodAddress(takeGoods.getAddress());
        }
        //检验下单用户信息是否有效，并拼装
        User publisher = userService.findUserById(indent.getPublisherId());
        if (publisher == null){
            log.error("[获取订单信息]publisherId不存在，indent={}", indent);
            throw new CheckException("订单信息有误，下单用户不存在");
        }
        School publisherSchool = schoolService.getById(publisher.getSchoolId());
        if (publisherSchool == null){
            log.error("[获取订单信息]publisherSchoolId不存在，indent={},\npublisher={}", indent, publisher);
            throw new CheckException("下单用户信息有误，请完善学校信息");
        }else {
            indentVO.setPublisherSchool(publisherSchool.getSchoolName());
        }
        indentVO.setPublisherGender(publisher.getGender());
        indentVO.setPublisherAvatar(publisher.getAvatar());
        indentVO.setPublisherNickName(publisher.getUserName());
        //检验该订单是否已被接，若被接，拼接上接单人信息
        if (indent.getPerformerId() != null){
            User performer = userService.findUserById(indent.getPerformerId());
            if (performer == null){
                log.error("[获取订单信息]performerId不存在，indent={}", indent);
                throw new CheckException("订单信息有误，接单人不存在");
            }
            School performerSchool = schoolService.getById(performer.getSchoolId());
            if (performerSchool == null){
                log.error("[获取订单信息]performerSchoolId不存在，indent={},\nperformer={}", indent, performer);
                throw new CheckException("接单用户信息有误，请完善学校信息");
            }else {
                indentVO.setPerformerSchool(performerSchool.getSchoolName());
            }
            indentVO.setPerformerGender(performer.getGender());
            indentVO.setPerformerAvatar(performer.getAvatar());
            indentVO.setPerformerNickName(performer.getUserName());
        }
        return indentVO;
    }

    private List<IndentVO> indent2VO(List<Indent> indents){
        List<IndentVO> indentVOS = new ArrayList<>();
        indents.forEach(e -> {
            try {
                indentVOS.add(indent2VO(e));
            } catch (CheckException ignored) {
            }
        });
        return indentVOS;
    }

    /**
     * 创建新订单
     */
    @Override
    public void create(Indent indent) {
        User user = userService.findUserById(indent.getPublisherId());
        if (user == null){
            //若用户id有误
            log.error("[创建订单]创建失败，下单用户不存在，publisherId={},indent={}", indent.getPublisherId(),indent);
            throw new SubstituteException("下单用户不存在，publisherId有误");
        }
        BigDecimal indentPrice = indent.getIndentPrice();
        BigDecimal userBalance = user.getBalance();
        if (userBalance.compareTo(indentPrice) < 0) {
            //若用户余额不足支付订单
            log.error("[创建订单]创建失败，用户余额不足，userOpenid={},userBalance={},indentPrice={}", user.getOpenid(),
                    userBalance, indentPrice);
            throw new SubstituteException("用户余额不足");
        }
        //扣钱
        user.setBalance(userBalance.subtract(indentPrice));
        userService.saveUser(user);
        //设置订单状态
        indent.setIndentState(IndentStateEnum.WAIT_FOR_PERFORMER);
        //将订单保存到数据库中
        indentDao.save(indent);
    }

    @Override
    public List<IndentVO> getWait(Integer sortType, GenderEnum sexType) {
        IndentSortTypeEnum sortTypeEnum = CommonUtil.getEnum(sortType, IndentSortTypeEnum.class);
        if (sortTypeEnum == null) {
            log.error("[获取订单列表]获取失败，sortType有误，sortType={}", sortType);
            throw new SubstituteException("sortType有误");
        }
        GenderEnum excludeGender;
        switch (sexType){
            case MALE:
                excludeGender = GenderEnum.FEMALE;
                break;
            case FEMALE:
                excludeGender = GenderEnum.MALE;
                break;
            default:
                //未知或异常字段时时报错
                log.error("[获取订单列表]获取失败，sexType有误，sexType={}", sexType);
                throw new SubstituteException("sexType有误");
        }
        List<Indent> indents;
        switch (sortTypeEnum) {
            case SORT_BY_TIME:
                indents = indentDao.findAllByIndentStateAndRequireGenderNotOrderByCreateTimeDesc(IndentStateEnum.WAIT_FOR_PERFORMER,excludeGender);
                break;
            case SORT_BY_PRICE:
                indents = indentDao.findAllByIndentStateAndRequireGenderNotOrderByIndentPriceDesc(IndentStateEnum.WAIT_FOR_PERFORMER,excludeGender);
                break;
            case SORT_BY_DEFAULT:
                List<Indent> temp = indentDao.findAllByIndentStateAndRequireGenderNot(IndentStateEnum.WAIT_FOR_PERFORMER,excludeGender);
                //随机打乱一下
                Random random = new Random();
                indents = new ArrayList<>();
                while (temp.size() > 0) {
                    int i = random.nextInt(temp.size());
                    indents.add(temp.get(i));
                    temp.remove(i);
                }
                break;
            default:
                log.error("[获取订单列表]获取失败，sortType有误，sortType={}", sortType);
                throw new SubstituteException("sortType有误");
        }
        indents.forEach(x -> {
            x.setCompanyName(null);
            x.setPickupCode(null);
        });
        return indent2VO(indents);
    }

    @Override
    public List<IndentVO> getUserPublishedIndent(String userId) {
        return indent2VO(indentDao.findByPublisherId(userId));
    }

    @Override
    public List<IndentVO> getUserPerformedIndent(String userId) {
        return indent2VO(indentDao.findByPerformerId(userId));
    }

    @Override
    public IndentVO getIndentDetail(Integer indentId, String userId) {
        Indent indent = indentDao.findByIndentId(indentId);
        if (!userId.equals(indent.getPerformerId()) && !userId.equals(indent.getPublisherId())) {
            //如果查询用户不是送货人 或 下单人 则将companyName、pickupCode置空
            indent.setCompanyName(null);
            indent.setPickupCode(null);
        }
        try {
            return indent2VO(indent);
        } catch (CheckException e) {
            log.error("[获取订单详情]获取失败，订单信息有误,message={},indent={}",e.getMessage(),indent);
            throw new SubstituteException(e.getMessage());
        }
    }

    @Override
    public void addIndentPrice(Integer indentId, String userId) {
        Indent indent = indentDao.findByIndentId(indentId);
        if (!indent.getPublisherId().equals(userId)) {
            //若不是下单人请求接口
            log.error("【增加赏金】增加失败，操作用户非下单人，userId={},publisherId={}", userId, indent.getPublisherId());
            throw new SubstituteException("无权限，该用户非下单人");
        }
        //查询用户余额是否足够
        User user = userService.findUserById(userId);
        if (user.getBalance().compareTo(new BigDecimal(1)) < 0) {
            log.error("【增加赏金】增加失败，余额不足，userId={},balance={}", userId, user.getBalance());
            throw new SubstituteException("增加失败，余额不足");
        }
        //扣钱
        userService.reduceBalance(userId, new BigDecimal(1));
        //增加订单悬赏金
        indent.setIndentPrice(indent.getIndentPrice().add(new BigDecimal(1)));
        indentDao.save(indent);
    }

    @Override
    public void takeIndent(Integer indentId, String userId) {
        User user = userService.findUserById(userId);
        if (user == null){
            log.error("【接单】接单失败，用户不存在，userId={}", userId);
            throw new SubstituteException("接单失败，用户不存在");
        }
        Indent indent = indentDao.findByIndentId(indentId);
        if (indent.getPerformerId() != null || indent.getIndentState() != IndentStateEnum.WAIT_FOR_PERFORMER){
            log.error("【接单】接单失败，该订单已被接，performerId={}, indentId={}, indentState={}",
                    indent.getPerformerId(),indent.getIndentId(),indent.getIndentState());
            throw new SubstituteException("接单失败，该订单已被接");
        }
    }

}
