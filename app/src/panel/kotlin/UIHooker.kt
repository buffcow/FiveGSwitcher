import android.annotation.SuppressLint
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
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import de.robv.android.xposed.XposedHelpers
import miui.telephony.TelephonyManager
import java.util.Locale
import kotlin.math.roundToInt


//
// Created by yangyiyu08 on 2023-01-15.
//
internal object UIHooker : YukiBaseHooker() {
    private val headerViewCache = mutableMapOf<String, View?>()

    private val telephonyManager: TelephonyManager by lazy {
        TelephonyManager.getDefault()
    }

    private val switchTitle: String
        get() {
            val s = Locale.getDefault().language.lowercase()
            return if (s.contains("zh")) "5G网络" else "5G Network"
        }

    private var configurationChanged = false

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

    private fun YukiMemberHookCreator.hookDetailHeaderView(headerLayoutName: String) {
        injectMember {
            method { name = "setupDetailHeader"; paramCount = 1 }
            afterHook {
                if (!telephonyManager.isFiveGCapable) {
                    removeSelf(); return@afterHook
                }

                val container =
                    if (instance !is ViewGroup) instance.javaClass.method {
                        name = "getView"; superClass(true)
                    }.get(instance).invoke<ViewGroup>()!!.getChildAt(0) as ViewGroup //MIUI13+
                    else (instance as ViewGroup).getChildAt(0) as ViewGroup //MIUI12

                val headerView = headerViewCache[headerLayoutName]

                headerView?.let { (it.parent as? ViewGroup)?.removeView(it) } //Must remove and add again

                val detailAdapter = args(0).any()
                if (!isCellularTile(detailAdapter)) return@afterHook

                if (headerView != null && !configurationChanged) {
                    refreshToggleState(headerView)
                    container.addView(headerView, 1)
                } else {
                    // create a new headerview
                    container.addView(
                        createHeaderView(container, headerLayoutName).also {
                            headerViewCache[headerLayoutName] = it
                        }, 1
                    )
                    if (configurationChanged) configurationChanged = false
                }

                hookDetailAdapter(detailAdapter) {
                    headerViewCache[headerLayoutName]?.let { refreshToggleState(it) }
                }
            }
        }

        injectMember {
            method { name = "onConfigurationChanged"; paramCount = 1; superClass() }
            afterHook { configurationChanged = true }
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
    private fun createHeaderView(container: ViewGroup, lname: String): View {
        val context = container.context
        val res = context.resources.getIdentifier(lname, "layout", context.packageName)
        return (LayoutInflater.from(context).inflate(res, container, false) as LinearLayout).apply {
            val toggle = findViewById(android.R.id.toggle)
                ?: (getChildAt(childCount - 1) as ViewStub).inflate() as CheckBox
            toggle.id = android.R.id.toggle
            toggle.isChecked = telephonyManager.isUserFiveGEnabled
            toggle.setOnCheckedChangeListener { _, isChecked ->
                telephonyManager.isUserFiveGEnabled = isChecked
            }
            (getChildAt(0) as TextView).apply { text = switchTitle }
            fixHeaderViewParams(this)
        }
    }

    private fun fixHeaderViewParams(v: View) {
        (v as LinearLayout).let { header ->
            // fix bottomMargin of root
            val params = header.layoutParams as LinearLayout.LayoutParams
            if (params.bottomMargin == 0) {
                val margin by lazy {
                    val density = header.context.resources.displayMetrics.density
                    (18 * density).roundToInt()
                }

                header.layoutParams = params.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    val mb = if (header.paddingStart > 0) header.paddingStart - 10 else margin
                    setMargins(marginStart, 0, marginEnd, mb)
                }
            }

            // fix weight of textview
            val view = header.getChildAt(1)
            if (view::class.simpleName == View::class.simpleName) {
                header.getChildAt(0).layoutParams = view.layoutParams
                header.removeView(view)
            }
        }
    }

    private fun hookDetailAdapter(detailAdapter: Any?, after: () -> Unit) {
        detailAdapter?.let {
            it.javaClass.hook {
                injectMember {
                    method { name = "onDetailItemClick"; paramCount = 1 }
                    afterHook { after.invoke() }
                }
            }
        }
    }

    private fun refreshToggleState(headerView: View) {
        headerView.findViewById<CheckBox>(android.R.id.toggle).isChecked =
            telephonyManager.isUserFiveGEnabled
    }
}
