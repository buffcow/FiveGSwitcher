import android.content.ComponentName
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.type.android.DrawableClass
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.qingyu.mi5g.R
import miui.telephony.TelephonyManager


//
// Created by yangyiyu08 on 2023-01-15.
//
internal object UIHooker : YukiBaseHooker() {
    private const val fiveGSpec = "5G"

    private val telephonyManager: TelephonyManager by lazy {
        TelephonyManager.getDefault()
    }

    override fun onHook() {
        "$packageName.qs.QSTileHost".hook {
            val injectCreateTile = injectMember {
                method { name = "createTile"; param(StringClass) }
                afterHook {
                    if (args(0).string() != fiveGSpec) return@afterHook
                    args(0).set("sync")
                    result = callOriginal()?.also {
                        it.current().method {
                            name = "setTileSpec"
                            param(StringClass)
                            superClass(true)
                        }.call(fiveGSpec)
                    }
                }
            }
            val injectQsStockTiles = injectMember {
                method { name = "getQsStockTiles" }
                afterHook { result = "${result<String>()},$fiveGSpec" }
            }.ignoredNoSuchMemberFailure() //ignored for MIUI12.5+

            fun injectQsStockTilesRes() { // hook res for MIUI12.5+
                fun getString(resName: String): String? = appResources!!.let {
                    @Suppress("DiscouragedApi")
                    val id = it.getIdentifier(resName, "string", packageName)
                    if (id != 0) it.getString(id) else null
                }

                resources().hook {
                    listOf("miui_%s", "miui_%s_pad").forEach {
                        val resName = String.format(it, "quick_settings_tiles_stock")
                        getString(resName)?.let {
                            injectResource {
                                conditions { name = resName; string() }
                                replaceTo("$it,$fiveGSpec")
                            }
                        } ?: loggerW(msg = "ignored hook res R.string.$resName")
                    }
                }
            }
            injectMember {
                method { name = "onTuningChanged" }
                beforeHook {
                    if (telephonyManager.isFiveGCapable.not()) {
                        removeSelf()
                        injectCreateTile.remove()
                        injectQsStockTiles.remove()
                        return@beforeHook
                    }
                    hookProxyTile()
                    injectQsStockTilesRes()
                }
            }
        }
    }

    private fun hookProxyTile() {
        val proxyTileClass by lazy {
            "$packageName.qs.tiles.SyncTile".toClass()
        }

        val getTileSpecMethod by lazy {
            proxyTileClass.method { name = "getTileSpec"; superClass(true) }
        }

        fun Any.isFiveGTile(): Boolean = getTileSpecMethod.get(this).string() == fiveGSpec

        proxyTileClass.hook {
            injectMember {
                method { name = "handleSetListening" }
                beforeHook { if (instance.isFiveGTile()) intercept() }
            }

            injectMember {
                method { name = "isAvailable" }
                beforeHook { if (instance.isFiveGTile()) resultTrue() }
            }

            injectMember {
                method { name = "getTileLabel" }
                beforeHook { if (instance.isFiveGTile()) result = fiveGSpec }
            }

            injectMember {
                method { name = "isSyncOn" }
                beforeHook {
                    if (instance.isFiveGTile().not()) return@beforeHook
                    result = telephonyManager.isUserFiveGEnabled
                }
            }

            injectMember {
                method { name = "handleClick" }
                beforeHook {
                    if (instance.isFiveGTile().not()) return@beforeHook
                    telephonyManager.isUserFiveGEnabled = !telephonyManager.isUserFiveGEnabled
                    instance.current().method { name = "refreshState"; superClass(true) }.call()
                    resultNull()
                }
            }

            injectMember {
                method { name = "getLongClickIntent" }
                beforeHook {
                    if (instance.isFiveGTile().not()) return@beforeHook
                    result = Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(
                            "com.android.phone",
                            "com.android.phone.settings.PreferredNetworkTypeListPreference"
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK xor Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                }
            }

            injectMember {
                method { name = "handleUpdateState" }
                afterHook {
                    if (instance.isFiveGTile().not()) return@afterHook
                    val iconCst by lazy {
                        "$packageName.qs.tileimpl.QSTileImpl\$DrawableIcon".toClass().constructor {
                            param(DrawableClass)
                        }.get()
                    }
                    val fiveGIconOn by lazy {
                        iconCst.call(moduleAppResources.getDrawable(R.drawable.ic_qs_5g_on, null))
                    }
                    val fiveGIconOff by lazy {
                        iconCst.call(moduleAppResources.getDrawable(R.drawable.ic_qs_5g_off, null))
                    }
                    args(0).any()!!.current {
                        val state = field { name = "value" }.boolean()
                        field { name = "label"; superClass(true) }.set(fiveGSpec)
                        field {
                            name = "icon"; superClass(true)
                        }.set(if (state) fiveGIconOn else fiveGIconOff)
                    }
                }
            }
        }
    }
}