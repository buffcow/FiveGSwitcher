package com.qingyu.mi5g

import android.content.ComponentName
import android.content.Intent
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.MembersType
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import miui.telephony.TelephonyManager


//
// Created by yangyiyu08 on 2022-11-03.
//
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    private val telephonyManager by lazy {
        TelephonyManager.getDefault()
    }

    override fun onInit() = configs {
        debugLog { isDebug = BuildConfig.DEBUG; tag = "FiveGSwitcher" }
    }

    override fun onHook() = encase {
        loadApp("com.android.phone") { hookFiveGSa() }
        loadApp("com.android.systemui") { hookQSTileHost() }
    }

    private fun PackageParam.hookFiveGSa() {
        "$packageName.FiveGManager".hook {
            injectMember {
                method { name = "setUserFiveGEnabled"; param(BooleanType) }
                afterHook {
                    instance.javaClass.method { name = "setUserFiveGSaEnabled" }.onNoSuchMethod {
                        // ignored
                    }.get(instance).call(args(0).boolean())
                }
            }
        }
    }

    private fun PackageParam.hookQSTileHost() {
        "$packageName.qs.QSTileHost".hook {
            injectMember {
                allMembers(MembersType.CONSTRUCTOR)
                afterHook { if (telephonyManager.isFiveGCapable) hookSyncTile() }
            }
        }
    }

    private fun PackageParam.hookSyncTile() {
        "$packageName.qs.tiles.SyncTile".hook {
            injectMember { method { name = "isAvailable" }; replaceToTrue() }

            injectMember { method { name = "handleSetListening" }; intercept() }

            injectMember {
                method { name = "isSyncOn" }
                replaceAny { telephonyManager.isUserFiveGEnabled }
            }

            injectMember {
                method { name = "handleClick" }
                replaceUnit {
                    telephonyManager.isUserFiveGEnabled = !telephonyManager.isUserFiveGEnabled
                    instance.javaClass.method {
                        name = "refreshState"; superClass(true)
                    }.get(instance).call()
                }
            }

            injectMember {
                method { name = "getLongClickIntent" }
                replaceAny {
                    Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(
                            "com.android.phone",
                            "com.android.phone.settings.PreferredNetworkTypeListPreference"
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK xor Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                }
            }

            resources().hook {
                listOf("qs", "cc_qs").forEach {
                    injectResource {
                        conditions { name = "ic_${it}_sync_on"; drawable() }
                        replaceToModuleResource(R.drawable.ic_qs_5g_on)
                    }
                    injectResource {
                        conditions { name = "ic_${it}_sync_off"; drawable() }
                        replaceToModuleResource(R.drawable.ic_qs_5g_off)
                    }
                }

                injectResource {
                    conditions { name = "quick_settings_sync_label"; string() }
                    replaceToModuleResource(R.string.quick_settings_5g_label)
                }
            }
        }
    }
}