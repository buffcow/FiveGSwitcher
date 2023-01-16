package com.qingyu.mi5g

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit


//
// Created by yangyiyu08 on 2022-11-03.
//
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        debugLog { isDebug = BuildConfig.DEBUG; tag = "FiveGSwitcher" }
    }

    override fun onHook() = encase {
        loadApp("com.android.phone", SAHooker)
        loadApp("com.android.systemui", UIHooker)
    }
}