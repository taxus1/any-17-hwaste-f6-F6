package com.somepro.interfaces.rest.hwaste;

import com.somepro.application.hwaste.WasteAlertAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hwaste.converter.WasteAlertVoConverter;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.WasteAlertVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 危废异常预警接口（用户接口层）：立预警 → 处置 → 关闭，以及多条件翻页。
 *
 * 在途超期（OVERDUE）只给运输中的联单立；重量差异（WEIGHT_DIFF）只给已签收的单子立；
 * 许可超限（QUOTA）只给签收后累计接收盖过许可上限的单子立。级别由系统按情况推出来。
 * 同一张联单配同一种类型只挂一条，重复提交不再新建。
 * 状态流转类接口的预警定位参数：alertId 或 alertNo 传其一即可。
 */
@RestController
@RequestMapping("/api/hwaste/alert")
public class WasteAlertController {

    private final WasteAlertAppService wasteAlertAppService;

    public WasteAlertController(WasteAlertAppService wasteAlertAppService) {
        this.wasteAlertAppService = wasteAlertAppService;
    }

    /**
     * 立预警：manifestId 或 manifestNo 传其一；alertType 限 OVERDUE / WEIGHT_DIFF / QUOTA；
     * detail 可空，处置过程中还能再补。同一张联单配同一种类型已挂过的，返回已挂的那条。
     */
    @PostMapping("/raise")
    public Mono<Result<WasteAlertVO>> raise(@RequestParam(required = false) Long manifestId,
                                            @RequestParam(required = false) String manifestNo,
                                            @RequestParam(required = false) String alertType,
                                            @RequestParam(required = false) String detail) {
        return wasteAlertAppService.raise(manifestId, manifestNo, alertType, detail)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 处置：alertId 或 alertNo 传其一；已发布 / 处置中 → 处置中，传了 detail 才把说明补上。 */
    @PostMapping("/handle")
    public Mono<Result<WasteAlertVO>> handle(@RequestParam(required = false) Long alertId,
                                             @RequestParam(required = false) String alertNo,
                                             @RequestParam(required = false) String detail) {
        return wasteAlertAppService.handle(alertId, alertNo, detail)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 关闭：alertId 或 alertNo 传其一；只有处置中的预警关得掉，落下关闭时刻。 */
    @PostMapping("/close")
    public Mono<Result<WasteAlertVO>> close(@RequestParam(required = false) Long alertId,
                                            @RequestParam(required = false) String alertNo) {
        return wasteAlertAppService.close(alertId, alertNo)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 翻预警：联单 / 类型 / 级别 / 状态随意拼，都不传则分页列全；每行带预警号。 */
    @GetMapping("/page")
    public Mono<Result<PageVO<WasteAlertVO>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) Long manifestId,
                                                   @RequestParam(required = false) String alertType,
                                                   @RequestParam(required = false) String alertLevel,
                                                   @RequestParam(required = false) String status) {
        return wasteAlertAppService.page(pageNum, pageSize, manifestId, alertType, alertLevel, status)
                .map(WasteAlertVoConverter::toPageVo)
                .map(Result::ok);
    }
}
