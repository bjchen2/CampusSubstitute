package com.wizzstudio.substitute.aspect;

import com.wizzstudio.substitute.dao.IndentDao;
import com.wizzstudio.substitute.dao.UserDao;
import com.wizzstudio.substitute.domain.Indent;
import com.wizzstudio.substitute.domain.User;
import com.wizzstudio.substitute.dto.wx.WxInfo;
import com.wizzstudio.substitute.enums.CountInfoTypeEnum;
import com.wizzstudio.substitute.form.IndentUserForm;
import com.wizzstudio.substitute.security.CustomUserDetails;
import com.wizzstudio.substitute.service.impl.ScheduledServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Created by Kikyou on 18-12-14
 */
@Aspect
@Slf4j
@Component
public class StatisticAspect {

    @Autowired
    private ScheduledServiceImpl scheduledService;
    @Autowired
    private UserDao userDao;
    @Autowired
    private IndentDao indentDao;

    @Pointcut("execution(public * com.wizzstudio.substitute.controller.LoginController.login(..))")
    public void loginBehavior() {
    }

    @Pointcut("execution(public * com.wizzstudio.substitute.controller.IndentController.publishNewIndent(..))")
    public void newIndentBehavior() {

    }

    @Pointcut("execution(public * com.wizzstudio.substitute.service.impl.IndentServiceImpl.finishedIndent(..))")
    public void indentFinished() {

    }


    @AfterReturning("loginBehavior()")
    public void addUserLoginCount(JoinPoint joinPoint) {
        if (joinPoint.getArgs()[0] instanceof WxInfo) {
            Integer schoolId = getSchoolId();
            scheduledService.update(schoolId, CountInfoTypeEnum.LOGIN_USER, 1);
        }
    }

    @AfterReturning("newIndentBehavior()")
    public void addNewIndentCount() {
        Integer schoolId = getSchoolId();
        scheduledService.update(schoolId, CountInfoTypeEnum.NEW_INDENT, 1);
    }

    @AfterReturning("indentFinished()")
    public void addFinishedCount(JoinPoint joinPoint) {
        Integer schoolId = getSchoolId();
        IndentUserForm indentUserForm = (IndentUserForm) (joinPoint.getArgs()[0]);
        Integer indentId = indentUserForm.getIndentId();
        if (indentId != null) {
            Indent indent = indentDao.findByIndentId(indentId);
            if (indent == null) return;
            scheduledService.update(schoolId, CountInfoTypeEnum.FINISHED_INDENT, 1);
        }
    }

    @AfterReturning(value = "execution(public * com.wizzstudio.substitute.service.impl.IndentServiceImpl.finishedIndent(..))", returning = "companyIncome")
    public void addCompanyIncome(JoinPoint joinPoint, Object companyIncome) {
        Integer schoolId = getSchoolId();
        if (schoolId == null) return;
        scheduledService.update(schoolId, CountInfoTypeEnum.INCOME, companyIncome);
    }

    public Integer getSchoolId() {
        String userId;
        try {
            userId = ((CustomUserDetails) (SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal())).getUsername();
        } catch (Exception e) {
            log.error("Exception Happend");
            e.printStackTrace();
            throw new AccessDeniedException("Access Denied");
        }
        if (userId == null) throw new AccessDeniedException("Access Denied");
        User user = userDao.findUserById(userId);
        if (user == null) throw new AccessDeniedException("Access Denied");
        return user.getSchoolId();
    }
}
