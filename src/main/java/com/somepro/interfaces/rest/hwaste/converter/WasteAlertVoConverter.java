package com.somepro.interfaces.rest.hwaste.converter;

import com.somepro.domain.hwaste.model.WasteAlert;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.WasteAlertVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * WasteAlert（领域）→ 对外 VO 转换器（用户接口层）。
 */
public final class WasteAlertVoConverter {

    private WasteAlertVoConverter() {
    }

    public static WasteAlertVO toVo(WasteAlert domain) {
        return new WasteAlertVO(
                domain.getId(),
                domain.getAlertNo(),
                domain.getManifestId(),
                domain.getAlertType() == null ? null : domain.getAlertType().name(),
                domain.getAlertLevel() == null ? null : domain.getAlertLevel().name(),
                domain.getStatus() == null ? null : domain.getStatus().name(),
                domain.getDetail(),
                domain.getRaisedAt(),
                domain.getClosedAt(),
                domain.getCreateTime());
    }

    public static PageVO<WasteAlertVO> toPageVo(PageResult<WasteAlert> page) {
        List<WasteAlertVO> content = page.content().stream()
                .map(WasteAlertVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
