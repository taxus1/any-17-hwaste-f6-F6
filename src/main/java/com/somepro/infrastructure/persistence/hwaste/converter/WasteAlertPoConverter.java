package com.somepro.infrastructure.persistence.hwaste.converter;

import com.somepro.domain.hwaste.model.AlertLevel;
import com.somepro.domain.hwaste.model.AlertStatus;
import com.somepro.domain.hwaste.model.AlertType;
import com.somepro.domain.hwaste.model.WasteAlert;
import com.somepro.infrastructure.persistence.hwaste.po.WasteAlertPO;

/**
 * WasteAlertPO（表）↔ WasteAlert（领域）转换器（基础设施层）。
 * 类型 / 级别 / 状态在库里存字符串，在领域里是枚举，互转在这里收口。
 */
public final class WasteAlertPoConverter {

    private WasteAlertPoConverter() {
    }

    public static WasteAlertPO toPo(WasteAlert domain) {
        WasteAlertPO po = new WasteAlertPO();
        po.setId(domain.getId());
        po.setAlertNo(domain.getAlertNo());
        po.setManifestId(domain.getManifestId());
        po.setAlertType(domain.getAlertType() == null ? null : domain.getAlertType().name());
        po.setAlertLevel(domain.getAlertLevel() == null ? null : domain.getAlertLevel().name());
        po.setStatus(domain.getStatus() == null ? null : domain.getStatus().name());
        po.setDetail(domain.getDetail());
        po.setRaisedAt(domain.getRaisedAt());
        po.setClosedAt(domain.getClosedAt());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static WasteAlert toDomain(WasteAlertPO po) {
        WasteAlert domain = new WasteAlert();
        domain.setId(po.getId());
        domain.setAlertNo(po.getAlertNo());
        domain.setManifestId(po.getManifestId());
        domain.setAlertType(po.getAlertType() == null ? null : AlertType.valueOf(po.getAlertType()));
        domain.setAlertLevel(po.getAlertLevel() == null ? null : AlertLevel.valueOf(po.getAlertLevel()));
        domain.setStatus(po.getStatus() == null ? null : AlertStatus.valueOf(po.getStatus()));
        domain.setDetail(po.getDetail());
        domain.setRaisedAt(po.getRaisedAt());
        domain.setClosedAt(po.getClosedAt());
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
