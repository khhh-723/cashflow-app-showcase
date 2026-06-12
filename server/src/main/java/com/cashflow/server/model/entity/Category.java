package com.cashflow.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("category")
public class Category {
    @TableId
    private String code;
    private String name;
    private String iconKey;
    private String colorHex;
    private Boolean isIncome;
    private Long monthlyBudgetCents;
    private Boolean isSystem;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIconKey() { return iconKey; }
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public Boolean getIsIncome() { return isIncome; }
    public void setIsIncome(Boolean isIncome) { this.isIncome = isIncome; }
    public Long getMonthlyBudgetCents() { return monthlyBudgetCents; }
    public void setMonthlyBudgetCents(Long monthlyBudgetCents) { this.monthlyBudgetCents = monthlyBudgetCents; }
    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }
}
