package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.WasteAlertPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * t_waste_alert 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 *
 * 编号取数说明：MAX 查询故意不过滤 del_flag —— 已删除预警的编号也不许复用；
 * FOR UPDATE 走当前已提交数据，避免事务快照里读到旧的最大号。
 */
@Mapper
public interface WasteAlertMapper extends BaseMapper<WasteAlertPO> {

    @Select("SELECT MAX(alert_no) FROM t_waste_alert WHERE alert_no LIKE CONCAT(#{prefix}, '%') FOR UPDATE")
    String maxAlertNo(@Param("prefix") String prefix);
}
