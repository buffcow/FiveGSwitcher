package com.qingyu.mi5g

import com.highcapable.yukihookapi.hook.factory.field
import de.robv.android.xposed.XposedHelpers


//
// Created by yangyiyu08 on 2023-01-15.
//
internal object UIHooker : BaseHooker() {
    override fun onHook() {
        hookQSDetail() //old style
        hookControlCenter() //new style
        hookCellularTile() //for single sim card
    }

    private fun hookQSDetail() {
        var detailClassName = "$packageName.qs.MiuiQSDetail" //MIUI13+
        if (!detailClassName.hasClass()) detailClassName = "$packageName.qs.QSDetail" //MIUI12
        detailClassName.hook { hookDetailHeaderView("qs_detail_header") }
    }

    private fun hookControlCenter() {
        var controlDetailClassName = "$packageName.miui.controlcenter.QSControlDetail" //MIUI12
        if (!controlDetailClassName.hasClass()) {
            //MIUI12.5 or old MIUI13
            controlDetailClassName = "$packageName.controlcenter.phone.detail.QSControlDetail"
        }
        controlDetailClassName.hook {
            hookDetailHeaderView("qs_control_detail_header") //Control center
        }.ignoredHookClassNotFoundFailure() //ignored class of MIUI14+

        hookPluginController() //MIUI13+ Control center plugin
    }

    private fun hookPluginController() {
        "$packageName.controlcenter.ControlCenter".hook {
            injectMember {
                method { name = "onPluginConnected"; paramCount = 1 }
                afterHook {
                    instance.javaClass.field {
                        name = "controlCenterPlugin" //MIUI14+
                    }.remedys {
                        field { name = "mControlCenterPlugin" } //MIUI13
                    }.get(instance).any()?.let {
                        findClass(
                            "miui.systemui.controlcenter.panel.detail.DetailPanelController",
                            it.javaClass.classLoader
                        ).hook { hookDetailHeaderView("detail_header") }
                    }
                }
            }.ignoredNoSuchMemberFailure() //ignored member of MIUI12.5
        }.ignoredHookClassNotFoundFailure() //ignored class of MIUI12
    }

    private fun hookCellularTile() {
        var tileClassName = "$packageName.qs.tiles.MiuiCellularTile" //MIUI12.5+
        if (!tileClassName.hasClass()) {
            tileClassName = "$packageName.qs.tiles.CellularTile" //MIUI12
        }
        tileClassName.hook {
            injectMember {
                method { name = "handleUpdateState"; paramCount = 2 }
                afterHook {
                    if (!telephonyManager.isFiveGCapable) {
                        removeSelf(); return@afterHook
                    }
                    args(0).any()?.let {
                        if (XposedHelpers.getIntField(it, "state") != 0) {
                            XposedHelpers.setBooleanField(it, "dualTarget", true)
                        }
                    }
                }
            }
        }
    }
}