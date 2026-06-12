package com.cashflow.server.service.ai;

import com.cashflow.server.tool.AiTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiChatServiceTest {

    @Test
    void repliesWithAndroidLocalLedgerSummaryWhenContextIsProvided() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long occurredAt = LocalDate.now(zone)
                .withDayOfMonth(5)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
        String ledgerContext = """
                {
                  "source": "android_local_room",
                  "officialReviewState": "CONFIRMED",
                  "entries": [
                    {
                      "id": "local-1",
                      "reviewState": "CONFIRMED",
                      "transactionType": "EXPENSE",
                      "amountCents": 2800,
                      "merchant": "奶茶",
                      "categoryName": "咖啡茶饮",
                      "accountName": "现金",
                      "occurredAt": %d,
                      "date": "%s"
                    }
                  ]
                }
                """.formatted(occurredAt, LocalDate.now(zone).withDayOfMonth(5));

        AiChatService service = new AiChatService(
                new TransactionDraftParser(),
                new ObjectMapper(),
                mock(AiTools.class),
                mock(RedisTemplate.class),
                "",
                "",
                "",
                0
        );

        AiChatService.AiChatReply reply = service.reply(1L, 1L, "我这个月消费多少了", ledgerContext);

        assertThat(reply.content()).contains("已确认支出为 28.00 元");
        assertThat(reply.content()).contains("统计只包含已确认入账");
    }
}
