package com.codex.suishouledger.domain

import com.codex.suishouledger.data.local.CategoryEntity

data class CategorySuggestion(
    val categoryCode: String,
    val confidence: Float
)

object CategorySuggestionEngine {
    private data class Rule(val code: String, val keywords: List<String>, val score: Float)

    private val rules = listOf(
        Rule("delivery", listOf("外卖", "饿了么", "美团外卖", "送达"), 0.97f),
        Rule("coffee", listOf("咖啡", "奶茶", "茶饮", "瑞幸", "星巴克"), 0.92f),
        Rule("food", listOf("餐厅", "餐饮", "午饭", "晚饭", "早饭", "饭店", "小吃", "饭"), 0.88f),
        Rule("transport", listOf("地铁", "公交", "火车", "高铁", "机票", "出租", "公交车", "出行"), 0.88f),
        Rule("taxi", listOf("打车", "滴滴", "高德打车", "曹操出行"), 0.95f),
        Rule("shopping", listOf("购物", "淘宝", "天猫", "京东", "拼多多", "超市"), 0.86f),
        Rule("daily", listOf("日用", "便利店", "生活用品"), 0.82f),
        Rule("entertainment", listOf("电影", "KTV", "游戏", "娱乐", "会员"), 0.85f),
        Rule("housing", listOf("房租", "物业", "房贷", "租金"), 0.95f),
        Rule("medical", listOf("医院", "药店", "医疗", "门诊"), 0.93f),
        Rule("education", listOf("培训", "课程", "学费", "教育", "书籍"), 0.9f),
        Rule("communication", listOf("话费", "流量", "宽带", "通信"), 0.9f),
        Rule("travel", listOf("酒店", "机票", "旅行", "景点"), 0.9f),
        Rule("clothing", listOf("衣服", "鞋", "服饰", "穿搭"), 0.84f),
        Rule("electronics", listOf("手机", "电脑", "耳机", "数码"), 0.9f),
        Rule("utilities", listOf("电费", "水费", "燃气", "话费", "缴费"), 0.9f),
        Rule("salary", listOf("工资", "薪资", "薪水", "月薪", "工资收入"), 0.95f),
        Rule("stock_income", listOf("股票", "炒股", "证券", "基金", "理财收益", "投资收益"), 0.92f),
        Rule("bonus_income", listOf("奖金", "年终奖", "绩效", "分红"), 0.9f),
        Rule("side_income", listOf("副业", "兼职", "稿费", "劳务", "佣金"), 0.88f),
        Rule("refund_income", listOf("退款", "返现", "退回"), 0.9f),
        Rule("other_income", listOf("收入", "收款"), 0.82f),
        Rule("transfer", listOf("转账", "红包", "收款"), 0.86f)
    )

    fun suggestCode(text: String, categories: List<CategoryEntity>): CategorySuggestion {
        val normalized = text.lowercase()
        val matched = rules.firstOrNull { rule ->
            categories.any { it.code == rule.code } &&
                rule.keywords.any { keyword -> normalized.contains(keyword.lowercase()) }
        }
        val incomeLike = listOf("收入", "收款", "工资", "薪资", "奖金", "退款", "返现").any {
            normalized.contains(it)
        }
        val fallback = if (incomeLike) {
            categories.firstOrNull { it.code == "other_income" }?.code
        } else {
            categories.firstOrNull { it.code == "other" }?.code
        } ?: "other"
        return if (matched == null) {
            CategorySuggestion(fallback, 0.42f)
        } else {
            CategorySuggestion(matched.code, matched.score)
        }
    }
}
