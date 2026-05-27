package com.aliucord.plugins

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.widgets.BottomSheet
import com.discord.views.CheckedSetting
import com.facebook.drawee.view.SimpleDraweeView

import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemEphemeralMessage
import com.discord.widgets.home.WidgetHomeHeaderManager
import com.discord.widgets.user.profile.UserProfileHeaderView
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.discord.widgets.friends.WidgetFriendsListAdapter

@AliucordPlugin
class HideAvatars : Plugin() {

    init {
        settingsTab = SettingsTab(HideAvatarsSettings::class.java, SettingsTab.Type.BOTTOM_SHEET).withArgs(settings)
    }

    override fun start(context: Context) {
        
        // This registers your custom slash command!
        commands.registerCommand("avatars", "Instantly toggle avatars on or off") {
            val currentState = settings.getBool("hide_avatars_toggle", true)
            val newState = !currentState
            settings.setBool("hide_avatars_toggle", newState)
            
            val status = if (newState) "HIDDEN" else "VISIBLE"
            CommandResult("Avatars are now $status! (Swipe to another channel and back to refresh)", null, false)
        }
        
        tryPatch("WidgetChatListAdapterItemMessage") {
            patcher.after<WidgetChatListAdapterItemMessage>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                if (!settings.getBool("hide_avatars_toggle", true)) return@after
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetChatListAdapterItemEphemeralMessage") {
            patcher.after<WidgetChatListAdapterItemEphemeralMessage>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                if (!settings.getBool("hide_avatars_toggle", true)) return@after
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetHomeHeaderManager") {
            patcher.after<WidgetHomeHeaderManager>(
                "onViewBound", View::class.java
            ) {
                if (!settings.getBool("hide_avatars_toggle", true)) return@after
                val view = it.args[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("UserProfileHeaderView") {
            patcher.after<UserProfileHeaderView>(
                "updateAvatar", String::class.java, Boolean::class.java
            ) {
                if (!settings.getBool("hide_avatars_toggle", true)) return@after
                (this as? ViewGroup)?.let { vg -> hideAllDraweeViews(vg) }
            }
        }

        tryPatch("WidgetUserSheet") {
            patcher.after<WidgetUserSheet>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
                if (!settings.getBool("hide_avatars_toggle", true)) return@after
                val view = it.args[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("WidgetFriendsListAdapter") {
            patcher.after<WidgetFriendsListAdapter>(
                "onBindViewHolder",
                RecyclerView.ViewHolder::class.java,
                Int::class.java
            ) {
                if (!settings.getBool("hide_avatars_toggle", true)) return@after
                val holder = it.args[0] as? RecyclerView.ViewHolder ?: return@after
                val root = holder.itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }

    private fun tryPatch(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.error("HideAvatars: unexpected error patching $name", e)
        }
    }

    private fun hideAllDraweeViews(root: ViewGroup) {
        for (i in 0 until root.childCount) {
            when (val child = root.getChildAt(i)) {
                is SimpleDraweeView -> child.visibility = View.INVISIBLE
                is ViewGroup        -> hideAllDraweeViews(child)
            }
        }
    }
}

class HideAvatarsSettings(private val settings: SettingsAPI) : BottomSheet() {
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        val context = view.context
        
        val switch = Utils.createCheckedSetting(context, CheckedSetting.ViewType.SWITCH, "Hide Avatars", "Instantly toggle all avatars on and off.")
        switch.isChecked = settings.getBool("hide_avatars_toggle", true)
        switch.setOnCheckedListener {
            settings.setBool("hide_avatars_toggle", it)
        }
        
        addView(switch)
    }
}
