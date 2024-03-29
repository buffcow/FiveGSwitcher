import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType


//
// Created by yangyiyu08 on 2023-01-15.
//
internal object SAHooker : YukiBaseHooker() {
    override fun onHook() {
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
}