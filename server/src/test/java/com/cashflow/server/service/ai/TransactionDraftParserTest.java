package com.cashflow.server.service.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDraftParserTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final TransactionDraftParser parser = new TransactionDraftParser(clock);

    @Test
    void parsesFoodExpenseWithAmountAndNote() {
        Optional<TransactionDraft> result = parser.parse("中午吃饭花了 35 块");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(3500L);
        assertThat(draft.transactionType()).isEqualTo("EXPENSE");
        assertThat(draft.categoryCode()).isEqualTo("food");
        assertThat(draft.categoryName()).isEqualTo("餐饮");
        assertThat(draft.note()).isEqualTo("中午吃饭花了 35 块");
        assertThat(draft.confidence()).isGreaterThanOrEqualTo(0.80f);
    }

    @Test
    void parsesDecimalTaxiExpense() {
        Optional<TransactionDraft> result = parser.parse("昨晚打车 27.5 元");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(2750L);
        assertThat(draft.categoryCode()).isEqualTo("taxi");
        assertThat(draft.categoryName()).isEqualTo("打车");
        assertThat(draft.transactionType()).isEqualTo("EXPENSE");
        assertThat(LocalDate.ofInstant(Instant.ofEpochMilli(draft.occurredAt()), ZoneId.of("Asia/Shanghai")))
                .isEqualTo(LocalDate.of(2026, 6, 3));
    }

    @Test
    void parsesIncome() {
        Optional<TransactionDraft> result = parser.parse("今天发工资 12000 元");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(1_200_000L);
        assertThat(draft.transactionType()).isEqualTo("INCOME");
        assertThat(draft.categoryCode()).isEqualTo("salary");
        assertThat(draft.categoryName()).isEqualTo("工资");
    }

    @Test
    void parsesDateWithoutTreatingYearAsAmount() {
        Optional<TransactionDraft> result = parser.parse("2026-06-08 星巴克 18 元，支付宝");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(1800L);
        assertThat(draft.categoryCode()).isEqualTo("coffee");
        assertThat(draft.accountCode()).isEqualTo("alipay");
        assertThat(LocalDate.ofInstant(Instant.ofEpochMilli(draft.occurredAt()), ZoneId.of("Asia/Shanghai")))
                .isEqualTo(LocalDate.of(2026, 6, 8));
    }

    @Test
    void parsesMonthDayBackfillExpense() {
        Optional<TransactionDraft> result = parser.parse("帮我补一笔6月5号的奶茶 28块");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(2800L);
        assertThat(draft.categoryCode()).isEqualTo("coffee");
        assertThat(LocalDate.ofInstant(Instant.ofEpochMilli(draft.occurredAt()), ZoneId.of("Asia/Shanghai")))
                .isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    void parsesBareAmountWhenIntentIsClear() {
        Optional<TransactionDraft> result = parser.parse("中午吃饭 35 微信");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(3500L);
        assertThat(draft.categoryCode()).isEqualTo("food");
        assertThat(draft.accountCode()).isEqualTo("wechat");
    }

    @Test
    void parsesRelativeTimeWithClock() {
        Optional<TransactionDraft> result = parser.parse("昨晚 21:30 打车 27.5 元");

        assertThat(result).isPresent();
        TransactionDraft draft = result.orElseThrow();
        assertThat(draft.amountCents()).isEqualTo(2750L);
        assertThat(LocalDateTime.ofInstant(Instant.ofEpochMilli(draft.occurredAt()), ZoneId.of("Asia/Shanghai")).getHour())
                .isEqualTo(21);
    }

    @Test
    void ignoresMessageWithoutMoney() {
        assertThat(parser.parse("这个月餐饮花了多少"))
                .isEmpty();
    }
}
