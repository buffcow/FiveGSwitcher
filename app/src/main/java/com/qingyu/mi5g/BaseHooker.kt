package com.qingyu.mi5g

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import miui.telephony.TelephonyManager
import java.util.Locale


//
// Created by yangyiyu08 on 2023-01-16.
//
abstract class BaseHooker : YukiBaseHooker() {
    private val headerViewCache = mutableMapOf<String, View?>()

    private val telephonyManager: TelephonyManager by lazy {
        TelephonyManager.getDefault()
    }

    private val switchTitle: String
        get() {
            val s = Locale.getDefault().language.lowercase()
            return if (s.contains("zh")) "5G网络" else "5G Network"
        }

    protected fun YukiMemberHookCreator.hookDetailHeaderView(headerLayoutName: String) {
        injectMember {
            method { name = "setupDetailHeader"; paramCount = 1 }
            afterHook {
                if (!telephonyManager.isFiveGCapable) {
                    removeSelf()
                    return@afterHook
                }

                val container =
                    if (instance !is ViewGroup) instance.javaClass.method {
                        name = "getView"; superClass(true)
                    }.get(instance).invoke<ViewGroup>()!!.getChildAt(0) as ViewGroup //MIUI13+
                    else (instance as ViewGroup).getChildAt(0) as ViewGroup //MIUI12

                val headerView = headerViewCache[headerLayoutName]

                headerView?.let { container.removeView(it) } //Must remove and add again

                if (!isCellularTile(args(0).any())) return@afterHook

                container.runCatching { addView(headerView, 1) }.onFailure {
                    // create or IllegalStateException
                    container.addView(
                        createHeaderView(container.context, headerLayoutName).also {
                            headerViewCache[headerLayoutName] = it
                        }, 1
                    )
                }
            }
        }
    }

    private fun isCellularTile(detailAdapter: Any?): Boolean {
        detailAdapter?.let {
            val intent = it.javaClass.method {
                name = "getSettingsIntent"
            }.get(detailAdapter).call() as Intent? ?: return false
            return intent.component?.packageName == "com.android.phone"
        }
        return false
    }

    @SuppressLint("DiscouragedApi")
    private fun createHeaderView(context: Context, lname: String): View {
        val res = context.resources.getIdentifier(lname, "layout", context.packageName)
        return LayoutInflater.from(context).inflate(res, null).apply {
            val title = ((this as ViewGroup).getChildAt(0) as TextView).apply {
                text = switchTitle
            }
            val toggle = this.findViewById(android.R.id.toggle) ?: this.let {
                (it.getChildAt(it.childCount - 1) as ViewStub).inflate() as CheckBox
            }
            toggle.isChecked = telephonyManager.isUserFiveGEnabled
            toggle.setOnCheckedChangeListener { _, isChecked ->
                telephonyManager.isUserFiveGEnabled = isChecked
            }
            if (childCount > 3) fixHeaderWeight(title)
        }
    }

    private fun ViewGroup.fixHeaderWeight(title: TextView) {
        title.layoutParams = LinearLayout.LayoutParams(0, -2).apply { weight = 1.0f }
        removeViewAt(1)
    }
}