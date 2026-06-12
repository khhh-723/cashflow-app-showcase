package com.cashflow.server.service.ai;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TransactionDraftParser {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern MONEY_WITH_UNIT_PATTERN = Pattern.compile(
            "(?:[￥¥]\\s*)?(?<!\\d)(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块钱|块|rmb|RMB|￥|¥)"
    );
    private static final Pattern MONEY_AFTER_VERB_PATTERN = Pattern.compile(
            "(?:花了?|消费|支付|支出|买(?:了)?|收入|收到|入账|转账|报销|退款)[^\\d]{0,8}(\\d+(?:\\.\\d{1,2})?)"
    );
    private static final Pattern BARE_MONEY_PATTERN = Pattern.compile(
            "(?<![\\d年月日号:/\\-])(\\d+(?:\\.\\d{1,2})?)(?![\\d年月日号:/\\-])"
    );
    private static final Pattern FULL_DATE_PATTERN = Pattern.compile(
            "(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})(?:[日号]?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"
    );
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[-/月](\\d{1,2})(?:[日号]?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final List<CategoryRule> CATEGORY_RULES = List.of(
            new CategoryRule("salary", "工资", true, "工资", "薪资", "发薪", "月薪"),
            new CategoryRule("bonus_income", "奖金", true, "奖金", "年终奖", "绩效"),
            new CategoryRule("refund_income", "退款返现", true, "退款", "返现", "报销", "退回"),
            new CategoryRule("side_income", "副业收入", true, "副业", "兼职", "外快"),
            new CategoryRule("taxi", "打车", false, "打车", "滴滴", "出租车", "网约车"),
            new CategoryRule("transport", "交通", false, "地铁", "公交", "高铁", "火车", "机票", "交通", "停车", "加油"),
            new CategoryRule("delivery", "外卖", false, "外卖", "美团", "饿了么"),
            new CategoryRule("coffee", "咖啡茶饮", false, "咖啡", "奶茶", "茶饮", "星巴克", "瑞幸"),
            new CategoryRule("food", "餐饮", false, "吃饭", "午饭", "晚饭", "早餐", "中饭", "餐", "饭", "食堂", "火锅", "烧烤", "餐饮"),
            new CategoryRule("shopping", "购物", false, "购物", "淘宝", "京东", "拼多多", "买了", "下单"),
            new CategoryRule("clothing", "服饰", false, "衣服", "裤子", "鞋", "服饰"),
            new CategoryRule("electronics", "数码", false, "手机", "电脑", "耳机", "数码"),
            new CategoryRule("medical", "医疗", false, "医院", "看病", "药", "医疗"),
            new CategoryRule("education", "教育", false, "课程", "学费", "书", "教育"),
            new CategoryRule("utilities", "生活缴费", false, "水费", "电费", "燃气", "话费", "网费", "物业"),
            new CategoryRule("housing", "居住", false, "房租", "租房", "房贷", "居住"),
            new CategoryRule("travel", "旅行", false, "酒店", "旅游", "旅行", "门票"),
            new CategoryRule("entertainment", "娱乐", false, "电影", "游戏", "演唱会", "娱乐"),
            new CategoryRule("other", "其他", false)
    );

    private final Clock clock;

    public TransactionDraftParser() {
        this(Clock.system(DEFAULT_ZONE));
    }

    TransactionDraftParser(Clock clock) {
        this.clock = clock;
    }

    public Optional<TransactionDraft> parse(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        Optional<Long> amount = extractAmountCents(content);
        if (amount.isEmpty()) {
            return Optional.empty();
        }

        CategoryRule category = matchCategory(content);
        boolean income = category.income() || containsAny(content, "收入", "收到", "入账", "发工资", "奖金", "退款", "返现");
        String transactionType = income ? "INCOME" : "EXPENSE";
        if (income && !category.income()) {
            category = matchIncomeFallback(content);
        }

        float confidence = calculateConfidence(content, category, amount.get());
        long occurredAt = inferOccurredAt(content);
        AccountGuess account = inferAccount(content);

        return Optional.of(new TransactionDraft(
                "ai-" + UUID.randomUUID(),
                amount.get(),
                transactionType,
                category.code(),
                category.name(),
                account.code(),
                account.name(),
                inferMerchant(content),
                content.trim(),
                "AI_CHAT",
                "DRAFT",
                occurredAt,
                confidence
        ));
    }

    private Optional<Long> extractAmountCents(String content) {
        Optional<Long> withUnit = extractLargestAmount(content, MONEY_WITH_UNIT_PATTERN);
        if (withUnit.isPresent()) {
            return withUnit;
        }
        Optional<Long> afterVerb = extractLargestAmount(content, MONEY_AFTER_VERB_PATTERN);
        if (afterVerb.isPresent()) {
            return afterVerb;
        }
        if (!looksLikeTransactionIntent(content)) {
            return Optional.empty();
        }
        return extractLargestAmount(content, BARE_MONEY_PATTERN);
    }

    private Optional<Long> extractLargestAmount(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        BigDecimal selected = null;
        while (matcher.find()) {
            BigDecimal candidate = new BigDecimal(matcher.group(1));
            if (candidate.compareTo(BigDecimal.ZERO) <= 0 || candidate.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
                continue;
            }
            if (selected == null || candidate.compareTo(selected) > 0) {
                selected = candidate;
            }
        }
        if (selected == null) {
            return Optional.empty();
        }
        return Optional.of(selected.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private boolean looksLikeTransactionIntent(String content) {
        return containsAny(
                content,
                "花", "消费", "支付", "买", "吃", "打车", "外卖", "咖啡", "奶茶", "购物",
                "收入", "收到", "发工资", "退款", "返现", "报销"
        );
    }

    private CategoryRule matchCategory(String content) {
        for (CategoryRule rule : CATEGORY_RULES) {
            if (rule.matches(content)) {
                return rule;
            }
        }
        return CATEGORY_RULES.get(CATEGORY_RULES.size() - 1);
    }

    private CategoryRule matchIncomeFallback(String content) {
        for (CategoryRule rule : CATEGORY_RULES) {
            if (rule.income() && rule.matches(content)) {
                return rule;
            }
        }
        if (containsAny(content, "退款", "返现", "报销")) {
            return new CategoryRule("refund_income", "退款返现", true);
        }
        return new CategoryRule("other_income", "其他收入", true);
    }

    private long inferOccurredAt(String content) {
        LocalDate date = LocalDate.now(clock);
        LocalTime time = LocalTime.MIDNIGHT;
        Matcher fullDate = FULL_DATE_PATTERN.matcher(content);
        if (fullDate.find()) {
            date = safeDate(
                    intGroup(fullDate, 1, date.getYear()),
                    intGroup(fullDate, 2, date.getMonthValue()),
                    intGroup(fullDate, 3, date.getDayOfMonth()),
                    date
            );
            time = safeTime(fullDate, 4, 5, 6, time);
            return LocalDateTime.of(date, time).atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        }

        Matcher monthDay = MONTH_DAY_PATTERN.matcher(content);
        if (monthDay.find()) {
            date = safeDate(
                    date.getYear(),
                    intGroup(monthDay, 1, date.getMonthValue()),
                    intGroup(monthDay, 2, date.getDayOfMonth()),
                    date
            );
            time = safeTime(monthDay, 3, 4, 5, time);
            return LocalDateTime.of(date, time).atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        }

        if (containsAny(content, "昨天", "昨晚", "昨日")) {
            date = date.minusDays(1);
        } else if (containsAny(content, "前天")) {
            date = date.minusDays(2);
        }
        Matcher timeMatcher = TIME_PATTERN.matcher(content);
        if (timeMatcher.find()) {
            time = safeTime(timeMatcher, 1, 2, 3, time);
        }
        return LocalDateTime.of(date, time).atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    private LocalDate safeDate(int year, int month, int day, LocalDate fallback) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private LocalTime safeTime(Matcher matcher, int hourGroup, int minuteGroup, int secondGroup, LocalTime fallback) {
        if (matcher.group(hourGroup) == null || matcher.group(minuteGroup) == null) {
            return fallback;
        }
        int hour = Math.max(0, Math.min(23, intGroup(matcher, hourGroup, fallback.getHour())));
        int minute = Math.max(0, Math.min(59, intGroup(matcher, minuteGroup, fallback.getMinute())));
        int second = Math.max(0, Math.min(59, intGroup(matcher, secondGroup, fallback.getSecond())));
        return LocalTime.of(hour, minute, second);
    }

    private int intGroup(Matcher matcher, int group, int fallback) {
        try {
            String value = matcher.group(group);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private AccountGuess inferAccount(String content) {
        if (containsAny(content, "微信", "wechat")) {
            return new AccountGuess("wechat", "微信");
        }
        if (containsAny(content, "支付宝", "alipay", "蚂蚁")) {
            return new AccountGuess("alipay", "支付宝");
        }
        if (containsAny(content, "银行卡", "银行", "信用卡", "储蓄卡")) {
            return new AccountGuess("bank", "银行卡");
        }
        return new AccountGuess("cash", "现金");
    }

    private String inferMerchant(String content) {
        for (String merchant : List.of("美团", "饿了么", "滴滴", "淘宝", "京东", "拼多多", "星巴克", "瑞幸")) {
            if (content.contains(merchant)) {
                return merchant;
            }
        }
        return null;
    }

    private float calculateConfidence(String content, CategoryRule category, long amountCents) {
        float confidence = 0.55f;
        if (amountCents > 0) {
            confidence += 0.25f;
        }
        if (!"other".equals(category.code()) && !"other_income".equals(category.code())) {
            confidence += 0.15f;
        }
        if (containsAny(content, "花", "消费", "支付", "买", "收入", "收到", "发工资")) {
            confidence += 0.05f;
        }
        return Math.min(confidence, 0.98f);
    }

    private boolean containsAny(String content, String... keywords) {
        String lower = content.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record CategoryRule(String code, String name, boolean income, String... keywords) {
        boolean matches(String content) {
            for (String keyword : keywords) {
                if (content.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record AccountGuess(String code, String name) {}
}
