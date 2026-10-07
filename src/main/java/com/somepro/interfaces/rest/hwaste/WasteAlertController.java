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
 * 危废异常预警接口（用户接口层）：立预警 → 处置（补说明）→ 关闭，以及明细与多条件翻页。
 *
 * 联单定位参数：manifestId 或 manifestNo 传其一；预警定位参数：alertId 或 alertNo 传其一。
 * 类型 alertType 限 OVERDUE / WEIGHT_DIFF / QUOTA，级别按情况推出、不由请求指定。
 */
@RestController
@RequestMapping("/api/hwaste/alert")
public class WasteAlertController {

    private final WasteAlertAppService wasteAlertAppService;

    public WasteAlertController(WasteAlertAppService wasteAlertAppService) {
        this.wasteAlertAppService = wasteAlertAppService;
    }

    /**
     * 立预警：alertType 传 OVERDUE / WEIGHT_DIFF / QUOTA，级别由系统按情况推出。
     * 在途超期只给运输中的联单立；重量差异只给已签收且实收与申报对不上的立；
     * 许可超限只给已签收且累计接收盖过许可上限的立。同联单同类型重复提交不新建，返回已挂那条。
     */
    @PostMapping("/raise")
    public Mono<Result<WasteAlertVO>> raise(@RequestParam(required = false) Long manifestId,
                                            @RequestParam(required = false) String manifestNo,
                                            @RequestParam(required = false) String alertType) {
        return wasteAlertAppService.raise(manifestId, manifestNo, alertType)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 处置：已发布 / 处置中可办，handlingNote 把处置说明补上；已关闭的不再动。 */
    @PostMapping("/handle")
    public Mono<Result<WasteAlertVO>> handle(@RequestParam(required = false) Long alertId,
                                             @RequestParam(required = false) String alertNo,
                                             @RequestParam(required = false) String handlingNote) {
        return wasteAlertAppService.handle(alertId, alertNo, handlingNote)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 关闭：处置完才关，落下关闭时刻；没处置过或已关闭的关不掉。 */
    @PostMapping("/close")
    public Mono<Result<WasteAlertVO>> close(@RequestParam(required = false) Long alertId,
                                            @RequestParam(required = false) String alertNo) {
        return wasteAlertAppService.close(alertId, alertNo)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 单查明细：alertId 或 alertNo 传其一。 */
    @GetMapping("/detail")
    public Mono<Result<WasteAlertVO>> detail(@RequestParam(required = false) Long alertId,
                                             @RequestParam(required = false) String alertNo) {
        return wasteAlertAppService.detail(alertId, alertNo)
                .map(WasteAlertVoConverter::toVo)
                .map(Result::ok);
    }

    /** 翻预警：联单 / 类型 / 级别 / 状态随意拼，都不传则分页列全；每行带预警编号。 */
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
