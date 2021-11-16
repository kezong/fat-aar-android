package com.kezong.demo.libaar

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.io.Serializable

enum class LibCountries(
    val code: String,
    @StringRes val nameRes: Int,
    @DrawableRes val flagRes: Int,
    vararg phonePrefix: String,
) : Serializable {

    ESTONIA("ee", R.string.country_ee, R.drawable.flag_ee, "+397"),
    USA("us", R.string.country_us, R.drawable.flag_us, "+1");

    fun getName(context: Context): String = context.getString(nameRes)

    companion object {

        @JvmStatic
        fun findByCode(countryCode: String?) = values().find { it.code.equals(countryCode, true) }
    }
}