package com.cobfa.app.data.local.db

import androidx.room.TypeConverter
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseSource
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType

class Converters {

    @TypeConverter
    fun fromExpenseType(type: ExpenseType): String = type.name

    @TypeConverter
    fun toExpenseType(value: String): ExpenseType =
        ExpenseType.valueOf(value)

    @TypeConverter
    fun fromExpenseCategory(category: ExpenseCategory?): String? =
        category?.name

    @TypeConverter
    fun toExpenseCategory(value: String?): ExpenseCategory? =
        value?.let { ExpenseCategory.valueOf(it) }

    @TypeConverter
    fun fromExpenseSource(source: ExpenseSource): String =
        source.name

    @TypeConverter
    fun toExpenseSource(value: String): ExpenseSource =
        ExpenseSource.valueOf(value)

    @TypeConverter
    fun fromExpenseStatus(status: ExpenseStatus): String =
        status.name

    @TypeConverter
    fun toExpenseStatus(value: String): ExpenseStatus =
        ExpenseStatus.valueOf(value)
}
