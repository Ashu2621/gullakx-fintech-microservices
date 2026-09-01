package com.gullakx.statement.web;

import com.gullakx.common.dto.ApiResponse;
import com.gullakx.statement.domain.StatementEntry;
import com.gullakx.statement.service.StatementProjector;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {

    private final StatementProjector projector;

    public StatementController(StatementProjector projector) {
        this.projector = projector;
    }

    public record LineResponse(long transferId, String direction, long counterparty,
                               long amountMinor, String currency, String occurredAt) {
    }

    public record StatementResponse(long walletId, long balanceMinor, List<LineResponse> lines) {
    }

    @GetMapping("/{walletId}")
    public ApiResponse<StatementResponse> statement(@PathVariable long walletId) {
        List<LineResponse> lines = projector.statementFor(walletId).stream()
                .map(e -> new LineResponse(e.getTransferId(), e.getDirection(), e.getCounterparty(),
                        e.getAmountMinor(), e.getCurrency(), e.getOccurredAt().toString()))
                .toList();
        return ApiResponse.success(
                new StatementResponse(walletId, projector.balanceFor(walletId), lines), "OK");
    }
}
