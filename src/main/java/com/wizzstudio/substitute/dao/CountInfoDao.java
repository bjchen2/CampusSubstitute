package com.wizzstudio.substitute.dao;

import com.wizzstudio.substitute.domain.CountInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created By Cx On 2018/11/20 0:17
 */
@Repository
public interface CountInfoDao extends JpaRepository<CountInfo, Integer> {
    List<CountInfo> getAllByCountDateBetweenOrderByCountDate(Integer from, Integer to);

    List<CountInfo> getAllByCountDateBetweenAndSchoolId(Integer from, Integer to, Integer schoolId);
}
