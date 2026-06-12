package com.cashflow.server.service.ai;

import com.cashflow.server.tool.AiTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AiChatService {

    private static final String REDIS_CONTEXT_PREFIX = "chat:ctx:";
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final String SYSTEM_PROMPT = "你是 CashFlow 的 AI 记账助手。\n\n"
            + "规则：\n"
            + "1. 用户表达新增账目时，优先调用 add_transaction 工具生成待确认草稿。\n"
            + "2. 用户查询历史账单时，调用 query_transactions。\n"
            + "3. 用户询问统计趋势时，调用 get_spending_summary。\n"
            + "4. 分类不确定时可调用 get_category_by_name 辅助判断。\n\n"
            + "所有新增账目都必须先返回待确认草稿，不要直接入账。\n"
            + "今天日期：" + java.time.LocalDate.now() + "。";

    private final TransactionDraftParser draftParser;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AiTools aiTools;
    private final RedisTemplate<String, String> redisTemplate;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int maxTokens;

    public AiChatService(
            TransactionDraftParser draftParser,
            ObjectMapper objectMapper,
            AiTools aiTools,
            RedisTemplate<String, String> redisTemplate,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.api-url}") String apiUrl,
            @Value("${deepseek.model}") String model,
            @Value("${deepseek.max-tokens}") int maxTokens) {
        this.draftParser = draftParser;
        this.objectMapper = objectMapper;
        this.aiTools = aiTools;
        this.redisTemplate = redisTemplate;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.restTemplate = new RestTemplate();
    }

    public AiChatReply reply(Long userId, Long sessionId, String userContent) {
        return reply(userId, sessionId, userContent, null);
    }

    public AiChatReply reply(Long userId, Long sessionId, String userContent, String ledgerContext) {
        try {
            Optional<AiChatReply> deterministicDraft = buildPendingDraftReply(userContent);
            if (deterministicDraft.isPresent()) {
                return deterministicDraft.get();
            }
            Optional<AiChatReply> localLedgerReply = buildLocalLedgerReply(userContent, ledgerContext);
            if (localLedgerReply.isPresent()) {
                return localLedgerReply.get();
            }
            List<Map<String, Object>> messages = buildMessages(sessionId, userContent, ledgerContext);
            List<Map<String, Object>> tools = buildToolDefinitions();
            DeepSeekResult result = callDeepSeekWithTools(messages, tools, userId, ledgerContext, 0);
            String toolCallsJson = result.toolCallsJson();
            return new AiChatReply(result.content(), toolCallsJson, result.tokenCount());
        } catch (Exception e) {
            return new AiChatReply("AI 服务暂时不可用：" + e.getMessage(), null, 0);
        }
    }

    public AiChatReply reply(String userContent) {
        return buildPendingDraftReply(userContent)
                .orElseGet(() -> new AiChatReply("我可以帮你用自然语言记账，也可以查询消费趋势。", null, 0));
    }

    private Optional<AiChatReply> buildPendingDraftReply(String userContent) {
        return draftParser.parse(userContent)
                .map(this::serializePendingDraft)
                .flatMap(payload -> payload.map(json ->
                        new AiChatReply("我识别到一笔待确认账目，请核对后入账。", json, 0)
                ));
    }

    private Optional<String> serializePendingDraft(TransactionDraft d) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "PENDING_CONFIRMATION");
            payload.put("tool", "add_transaction_draft");
            payload.put("clientId", d.clientId());
            payload.put("amountCents", d.amountCents());
            payload.put("amountYuan", String.format(Locale.CHINA, "%.2f", d.amountCents() / 100.0));
            payload.put("transactionType", d.transactionType());
            payload.put("categoryCode", d.categoryCode());
            payload.put("categoryName", d.categoryName());
            payload.put("accountCode", d.accountCode());
            payload.put("accountName", d.accountName());
            payload.put("merchant", d.merchant() != null ? d.merchant() : "");
            payload.put("note", d.note());
            payload.put("sourceType", d.sourceType());
            payload.put("reviewState", d.reviewState());
            payload.put("occurredAt", d.occurredAt());
            payload.put("confidence", d.confidence());
            return Optional.of(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private List<Map<String, Object>> buildMessages(Long sessionId, String userContent, String ledgerContext) {
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", SYSTEM_PROMPT);
        messages.add(system);

        if (ledgerContext != null && !ledgerContext.isBlank()) {
            Map<String, Object> ledgerSystem = new LinkedHashMap<>();
            ledgerSystem.put("role", "system");
            ledgerSystem.put(
                    "content",
                    "Android local Room ledger context is attached. For bill queries and spending summaries, "
                            + "use query_transactions or get_spending_summary and prioritize CONFIRMED local entries "
                            + "over remote database rows."
            );
            messages.add(ledgerSystem);
        }

        String redisKey = REDIS_CONTEXT_PREFIX + sessionId;
        List<String> context = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (context != null) {
            for (String entry : context) {
                int idx = entry.indexOf(": ");
                if (idx > 0) {
                    String role = entry.substring(0, idx).toLowerCase();
                    String text = entry.substring(idx + 2);
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", role);
                    msg.put("content", text);
                    messages.add(msg);
                }
            }
        }

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        return messages;
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> addTx = new LinkedHashMap<>();
        addTx.put("type", "function");
        Map<String, Object> addFn = new LinkedHashMap<>();
        addFn.put("name", "add_transaction");
        addFn.put("description", "从用户自然语言中提取一笔待确认记账草稿，不直接写入正式账本。");
        Map<String, Object> addParams = new LinkedHashMap<>();
        addParams.put("type", "object");
        Map<String, Object> addProps = new LinkedHashMap<>();
        addProps.put("content", Map.of("type", "string", "description", "用户原始记账句子，包含金额、用途、账户、时间等线索。"));
        addProps.put("date", Map.of("type", "string", "description", "可选交易日期，格式 YYYY-MM-DD。"));
        addParams.put("properties", addProps);
        addParams.put("required", List.of("content"));
        addFn.put("parameters", addParams);
        addTx.put("function", addFn);
        tools.add(addTx);

        Map<String, Object> qTx = new LinkedHashMap<>();
        qTx.put("type", "function");
        Map<String, Object> qFn = new LinkedHashMap<>();
        qFn.put("name", "query_transactions");
        qFn.put("description", "查询用户历史流水，用于回答账单明细问题。");
        Map<String, Object> qParams = new LinkedHashMap<>();
        qParams.put("type", "object");
        Map<String, Object> qProps = new LinkedHashMap<>();
        qProps.put("start_date", Map.of("type", "string", "description", "开始日期，格式 YYYY-MM-DD。"));
        qProps.put("end_date", Map.of("type", "string", "description", "结束日期，格式 YYYY-MM-DD。"));
        qProps.put("category", Map.of("type", "string", "description", "可选分类名称。"));
        qProps.put("type", Map.of("type", "string", "enum", List.of("EXPENSE", "INCOME"), "description", "收支类型。"));
        qParams.put("properties", qProps);
        qParams.put("required", List.of());
        qFn.put("parameters", qParams);
        qTx.put("function", qFn);
        tools.add(qTx);

        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("type", "function");
        Map<String, Object> smFn = new LinkedHashMap<>();
        smFn.put("name", "get_spending_summary");
        smFn.put("description", "汇总用户消费或收入统计，用于回答趋势、分类占比和总额问题。");
        Map<String, Object> smParams = new LinkedHashMap<>();
        smParams.put("type", "object");
        Map<String, Object> smProps = new LinkedHashMap<>();
        smProps.put("start_date", Map.of("type", "string", "description", "开始日期，格式 YYYY-MM-DD。"));
        smProps.put("end_date", Map.of("type", "string", "description", "结束日期，格式 YYYY-MM-DD。"));
        smProps.put("group_by", Map.of("type", "string", "enum", List.of("category", "day", "month"), "description", "统计维度。"));
        smParams.put("properties", smProps);
        smParams.put("required", List.of());
        smFn.put("parameters", smParams);
        sm.put("function", smFn);
        tools.add(sm);

        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("type", "function");
        Map<String, Object> catFn = new LinkedHashMap<>();
        catFn.put("name", "get_category_by_name");
        catFn.put("description", "按分类名称模糊匹配系统分类。");
        Map<String, Object> catParams = new LinkedHashMap<>();
        catParams.put("type", "object");
        Map<String, Object> catProps = new LinkedHashMap<>();
        catProps.put("name", Map.of("type", "string", "description", "分类关键词。"));
        catParams.put("properties", catProps);
        catParams.put("required", List.of("name"));
        catFn.put("parameters", catParams);
        cat.put("function", catFn);
        tools.add(cat);

        return tools;
    }

    @SuppressWarnings("unchecked")
    private DeepSeekResult callDeepSeekWithTools(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Long userId,
            String ledgerContext,
            int round) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.7);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(apiUrl, entity, Map.class);
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null || !respBody.containsKey("choices")) {
                return new DeepSeekResult("AI 返回内容为空", null, 0);
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
            if (choices.isEmpty()) {
                return new DeepSeekResult("AI 暂时没有返回结果", null, 0);
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = (String) message.getOrDefault("content", "");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

            Map<String, Object> usage = (Map<String, Object>) respBody.get("usage");
            int tokenCount = usage != null ? ((Number) usage.getOrDefault("total_tokens", 0)).intValue() : 0;

            String toolCallsJson = null;
            if (toolCalls != null && !toolCalls.isEmpty()) {
                try { toolCallsJson = objectMapper.writeValueAsString(toolCalls); }
                catch (JsonProcessingException ignored) {}
            }

            if (toolCalls == null || toolCalls.isEmpty() || round >= MAX_TOOL_ROUNDS) {
                return new DeepSeekResult(content != null ? content : "", toolCallsJson, tokenCount);
            }

            messages.add(message);

            for (Map<String, Object> tc : toolCalls) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String fnName = (String) func.get("name");
                String args = (String) func.get("arguments");
                String tcId = (String) tc.get("id");

                String result = executeTool(fnName, args, userId, ledgerContext);
                if ("add_transaction".equals(fnName) && isPendingTransactionDraft(result)) {
                    return new DeepSeekResult("我识别到一笔待确认账目，请核对后生成草稿。", result, tokenCount);
                }
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("role", "tool");
                tm.put("tool_call_id", tcId);
                tm.put("content", result);
                messages.add(tm);
            }

            return callDeepSeekWithTools(messages, tools, userId, ledgerContext, round + 1);

        } catch (Exception e) {
            return new DeepSeekResult("AI 调用失败: " + e.getMessage(), null, 0);
        }
    }

    private String executeTool(String name, String args, Long userId, String ledgerContext) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(args, Map.class);
            return switch (name) {
                case "add_transaction" -> execAddTx(parsed);
                case "query_transactions" -> execQueryTx(parsed, userId, ledgerContext);
                case "get_spending_summary" -> execSummary(parsed, userId, ledgerContext);
                case "get_category_by_name" -> execCategory(parsed);
                default -> "{\"error\":\"unknown tool: " + name + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String execAddTx(Map<String, Object> args) {
        String content = (String) args.getOrDefault("content", "");
        var draft = draftParser.parse(content);
        if (draft.isPresent()) {
            return serializePendingDraft(draft.get()).orElse("{\"error\":\"serialization failed\"}");
        }
        return "{\"status\":\"UNRECOGNIZED\",\"message\":\"未识别到明确的金额或账目内容\"}";
    }

    private boolean isPendingTransactionDraft(String result) {
        try {
            Map<String, Object> payload = objectMapper.readValue(result, Map.class);
            return "PENDING_CONFIRMATION".equals(payload.get("status"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String execQueryTx(Map<String, Object> args, Long userId, String ledgerContext) {
        String sd = (String) args.getOrDefault("start_date", "");
        String ed = (String) args.getOrDefault("end_date", "");
        if (sd.isBlank()) sd = java.time.LocalDate.now().withDayOfMonth(1).toString();
        if (ed.isBlank()) ed = java.time.LocalDate.now().toString();
        long st = java.time.LocalDate.parse(sd).atStartOfDay()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long et = java.time.LocalDate.parse(ed).plusDays(1).atStartOfDay()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        var localTxs = queryLocalLedger(ledgerContext, st, et, (String) args.get("type"), (String) args.get("category"));
        if (localTxs != null) {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "status", "SUCCESS",
                        "source", "android_local_room",
                        "count", localTxs.size(),
                        "transactions", localTxs
                ));
            } catch (JsonProcessingException e) {
                return "{\"error\":\"serialization failed\"}";
            }
        }
        var txs = aiTools.queryTransactions(userId, st, et);
        try {
            return objectMapper.writeValueAsString(Map.of("status","SUCCESS","count",txs.size(),"transactions",txs));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private String execSummary(Map<String, Object> args, Long userId, String ledgerContext) {
        String sd = (String) args.getOrDefault("start_date", "");
        String ed = (String) args.getOrDefault("end_date", "");
        if (sd.isBlank()) sd = java.time.LocalDate.now().withDayOfMonth(1).toString();
        if (ed.isBlank()) ed = java.time.LocalDate.now().toString();
        long st = java.time.LocalDate.parse(sd).atStartOfDay()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long et = java.time.LocalDate.parse(ed).plusDays(1).atStartOfDay()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        var localTxs = queryLocalLedger(ledgerContext, st, et, null, null);
        if (localTxs != null) {
            var r = summarizeLocalLedger(localTxs, sd, ed, (String) args.get("group_by"));
            try { return objectMapper.writeValueAsString(r); }
            catch (JsonProcessingException e) { return "{\"error\":\"serialization failed\"}"; }
        }
        var r = aiTools.getSpendingSummary(userId, st, et, (String) args.get("group_by"));
        try { return objectMapper.writeValueAsString(r); }
        catch (JsonProcessingException e) { return "{\"error\":\"serialization failed\"}"; }
    }

    private String execCategory(Map<String, Object> args) {
        String name = (String) args.getOrDefault("name", "");
        if (name.isBlank()) return "{\"categories\":[]}";
        var cats = aiTools.getCategoryByName(name);
        try { return objectMapper.writeValueAsString(Map.of("categories", cats)); }
        catch (JsonProcessingException e) { return "{\"categories\":[]}"; }
    }

    private Optional<AiChatReply> buildLocalLedgerReply(String userContent, String ledgerContext) {
        if (!looksLikeLocalLedgerQuestion(userContent)) {
            return Optional.empty();
        }
        LocalDate start = inferStartDate(userContent);
        LocalDate end = inferEndDate(userContent, start);
        long st = start.atStartOfDay().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long et = end.plusDays(1).atStartOfDay().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        var txs = queryLocalLedger(ledgerContext, st, et, null, null);
        if (txs == null) {
            return Optional.empty();
        }
        long expense = sumByType(txs, "EXPENSE");
        long income = sumByType(txs, "INCOME");
        String content = "本地账本中，" + dateRangeLabel(start, end) + "，"
                + "已确认支出为 " + yuan(expense) + " 元，收入为 " + yuan(income) + " 元。";
        if (txs.isEmpty()) {
            content += "目前没有已确认流水。";
        } else {
            content += "统计只包含已确认入账的记录。";
        }
        return Optional.of(new AiChatReply(content, null, 0));
    }

    private boolean looksLikeLocalLedgerQuestion(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return (normalized.contains("消费") || normalized.contains("花了") || normalized.contains("花多少")
                || normalized.contains("支出") || normalized.contains("收入") || normalized.contains("账单"))
                && (normalized.contains("多少") || normalized.contains("合计") || normalized.contains("总")
                || normalized.contains("这个月") || normalized.contains("本月") || normalized.contains("今天")
                || normalized.contains("昨天"));
    }

    private LocalDate inferStartDate(String text) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (text != null && (text.contains("今天") || text.contains("今日"))) {
            return today;
        }
        if (text != null && (text.contains("昨天") || text.contains("昨日"))) {
            return today.minusDays(1);
        }
        return today.withDayOfMonth(1);
    }

    private LocalDate inferEndDate(String text, LocalDate start) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (text != null && (text.contains("今天") || text.contains("今日") || text.contains("昨天") || text.contains("昨日"))) {
            return start;
        }
        return today;
    }

    private String dateRangeLabel(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return start.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return start.format(DateTimeFormatter.ISO_LOCAL_DATE) + " 至 " + end.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryLocalLedger(
            String ledgerContext,
            long startTime,
            long endTime,
            String type,
            String category) {
        if (ledgerContext == null || ledgerContext.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(ledgerContext, Map.class);
            Object entriesObj = root.get("entries");
            if (!(entriesObj instanceof List<?> entries)) {
                return null;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : entries) {
                if (!(item instanceof Map<?, ?> raw)) continue;
                Map<String, Object> entry = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    if (e.getKey() != null) entry.put(String.valueOf(e.getKey()), e.getValue());
                }
                if (!"CONFIRMED".equalsIgnoreCase(asString(entry.get("reviewState")))) continue;
                long occurredAt = asLong(entry.get("occurredAt"), 0L);
                if (occurredAt < startTime || occurredAt >= endTime) continue;
                String txType = asString(entry.get("transactionType"));
                if (type != null && !type.isBlank() && !type.equalsIgnoreCase(txType)) continue;
                String categoryName = asString(entry.get("categoryName"));
                String categoryCode = asString(entry.get("categoryCode"));
                if (category != null && !category.isBlank()
                        && !categoryName.contains(category)
                        && !categoryCode.equalsIgnoreCase(category)) continue;
                result.add(formatLocalTransaction(entry));
            }
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> formatLocalTransaction(Map<String, Object> entry) {
        Map<String, Object> m = new LinkedHashMap<>();
        long amountCents = asLong(entry.get("amountCents"), 0L);
        m.put("amountYuan", yuan(amountCents));
        m.put("amountCents", amountCents);
        m.put("merchant", asString(entry.get("merchant")));
        m.put("categoryName", asString(entry.get("categoryName")));
        m.put("categoryCode", asString(entry.get("categoryCode")));
        m.put("accountName", asString(entry.get("accountName")));
        m.put("accountCode", asString(entry.get("accountCode")));
        m.put("transactionType", asString(entry.get("transactionType")));
        m.put("note", asString(entry.get("note")));
        m.put("occurredAt", asLong(entry.get("occurredAt"), 0L));
        m.put("date", asString(entry.get("date")));
        return m;
    }

    private Map<String, Object> summarizeLocalLedger(
            List<Map<String, Object>> txs,
            String startDate,
            String endDate,
            String groupBy) {
        Map<String, Object> result = new LinkedHashMap<>();
        long expense = sumByType(txs, "EXPENSE");
        long income = sumByType(txs, "INCOME");
        result.put("status", "SUCCESS");
        result.put("source", "android_local_room");
        result.put("totalExpenseYuan", yuan(expense));
        result.put("totalIncomeYuan", yuan(income));
        result.put("netYuan", yuan(income - expense));
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("count", txs.size());
        if (groupBy != null && !groupBy.isBlank()) {
            result.put("groupBy", groupBy);
            result.put("groups", groupLocalTransactions(txs, groupBy));
        }
        return result;
    }

    private List<Map<String, Object>> groupLocalTransactions(List<Map<String, Object>> txs, String groupBy) {
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (Map<String, Object> tx : txs) {
            String key = switch (groupBy) {
                case "day" -> asString(tx.get("date"));
                case "month" -> asString(tx.get("date")).length() >= 7 ? asString(tx.get("date")).substring(0, 7) : "";
                default -> asString(tx.get("categoryName")).isBlank() ? "未分类" : asString(tx.get("categoryName"));
            };
            long[] totals = grouped.computeIfAbsent(key, ignored -> new long[2]);
            if ("INCOME".equalsIgnoreCase(asString(tx.get("transactionType")))) {
                totals[1] += asLong(tx.get("amountCents"), 0L);
            } else if ("EXPENSE".equalsIgnoreCase(asString(tx.get("transactionType")))) {
                totals[0] += asLong(tx.get("amountCents"), 0L);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((key, totals) -> result.add(Map.of(
                "label", key,
                "expenseYuan", yuan(totals[0]),
                "incomeYuan", yuan(totals[1])
        )));
        return result;
    }

    private long sumByType(List<Map<String, Object>> txs, String type) {
        long total = 0L;
        for (Map<String, Object> tx : txs) {
            if (type.equalsIgnoreCase(asString(tx.get("transactionType")))) {
                total += asLong(tx.get("amountCents"), 0L);
            }
        }
        return total;
    }

    private String yuan(long cents) {
        return String.format(Locale.CHINA, "%.2f", cents / 100.0);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(asString(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record AiChatReply(String content, String toolCalls, int tokenCount) {}
    private record DeepSeekResult(String content, String toolCallsJson, int tokenCount) {}
}
