package com.codex.suishouledger.data.local

object DefaultCategories {
    fun all(): List<CategoryEntity> = listOf(
        CategoryEntity("food", "餐饮", "restaurant", "#EF4444", 10),
        CategoryEntity("coffee", "咖啡茶饮", "local_cafe", "#7C3AED", 11),
        CategoryEntity("delivery", "外卖", "delivery_dining", "#F97316", 12),
        CategoryEntity("transport", "交通", "directions_transit", "#2563EB", 20),
        CategoryEntity("taxi", "打车", "local_taxi", "#0F766E", 21),
        CategoryEntity("shopping", "购物", "shopping_bag", "#DB2777", 30),
        CategoryEntity("daily", "日用", "receipt_long", "#4B5563", 31),
        CategoryEntity("entertainment", "娱乐", "movie", "#8B5CF6", 40),
        CategoryEntity("housing", "居住", "home", "#0891B2", 50),
        CategoryEntity("medical", "医疗", "local_hospital", "#DC2626", 60),
        CategoryEntity("education", "教育", "school", "#4F46E5", 70),
        CategoryEntity("communication", "通讯", "wifi", "#14B8A6", 80),
        CategoryEntity("travel", "旅行", "flight_takeoff", "#0EA5E9", 90),
        CategoryEntity("clothing", "服饰", "checkroom", "#9333EA", 100),
        CategoryEntity("electronics", "数码", "devices", "#1D4ED8", 110),
        CategoryEntity("utilities", "生活缴费", "bolt", "#EA580C", 120),
        CategoryEntity("transfer", "转账", "swap_horiz", "#6B7280", 130, isIncome = false),
        CategoryEntity("salary", "工资", "payments", "#16A34A", 140, isIncome = true),
        CategoryEntity("stock_income", "炒股收入", "show_chart", "#0EA5E9", 141, isIncome = true),
        CategoryEntity("bonus_income", "奖金", "redeem", "#22C55E", 142, isIncome = true),
        CategoryEntity("side_income", "副业收入", "work", "#14B8A6", 143, isIncome = true),
        CategoryEntity("refund_income", "退款返现", "undo", "#84CC16", 144, isIncome = true),
        CategoryEntity("other_income", "其他收入", "savings", "#64748B", 149, isIncome = true),
        CategoryEntity("other", "其他", "more_horiz", "#64748B", 999)
    )
}
