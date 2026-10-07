package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.WasteAlert;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 危废异常预警仓储端口：领域层定义，基础设施层实现。
 *
 * 立预警的「查重 + 取号 + 插入」要保证并发安全，由实现侧用 WA 锁兜底
 * （同一张联单同一类型几乎同时立，只该成一条；同一个预警号只成一单）。
 * 处置 / 关闭走条件更新：只认源状态，后到的请求更新 0 行即拒。
 */
public interface WasteAlertRepository {

    /**
     * 立预警：锁内先查同联单 + 同类型是否已有（含已关闭的，一条类型只挂一条），
     * 已有则原样返回不新建；否则分配预警编号（WA-年份-序号）落库，状态 RAISED。
     */
    Mono<WasteAlert> createIfAbsent(WasteAlert alert);

    Mono<WasteAlert> findById(Long id);

    Mono<WasteAlert> findByAlertNo(String alertNo);

    /** 同联单 + 同类型是否已挂过一条（不论状态）。 */
    Mono<WasteAlert> findByManifestAndType(Long manifestId, String alertType);

    /** 处置：条件更新仅 RAISED / HANDLING → HANDLING，补说明；已关闭更新 0 行即拒。 */
    Mono<WasteAlert> handle(WasteAlert alert);

    /** 关闭：条件更新仅 HANDLING → CLOSED，落关闭时刻；其它状态更新 0 行即拒。 */
    Mono<WasteAlert> close(WasteAlert alert);

    /** 多条件分页：联单 / 类型 / 级别 / 状态均可选，一个都不传则分页列全；每行带预警号。 */
    Mono<PageResult<WasteAlert>> page(int pageNum, int pageSize, Long manifestId, String alertType,
                                      String alertLevel, String status);
}
