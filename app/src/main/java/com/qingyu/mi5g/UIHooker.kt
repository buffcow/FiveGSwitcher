package com.qingyu.mi5g

import com.highcapable.yukihookapi.hook.factory.field


//
// Created by yangyiyu08 on 2023-01-15.
//
internal object UIHooker : BaseHooker() {
    override fun onHook() {
        hookQSDetail() //old style
        hookControlCenter() //new style
    }

    private fun hookQSDetail() {
        var detailClassName = "$packageName.qs.QSDetail" //MIUI12
        if (!detailClassName.hasClass()) detailClassName = "$packageName.qs.MiuiQSDetail" //MIUI13+
        detailClassName.hook { hookDetailHeaderView("qs_detail_header") }
    }

    private fun hookControlCenter() {
        var controlDetailClassName = "$packageName.miui.controlcenter.QSControlDetail"
        if (!controlDetailClassName.hasClass()) {
            //MIUI12.5 or old MIUI13
            controlDetailClassName = "$packageName.controlcenter.phone.detail.QSControlDetail"
        }
        controlDetailClassName.hook {
            hookDetailHeaderView("qs_control_detail_header") //MIUI12 Control center
        }.onHookClassNotFoundFailure { hookPluginController() } //MIUI13+ Control center plugin
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
            }
        }
    }
}