package com.aliucord.plugins

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.facebook.drawee.view.SimpleDraweeView

import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemEphemeralMessage
import com.discord.widgets.home.WidgetHomeHeaderManager
import com.discord.widgets.user.profile.UserProfileHeaderView
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.discord.widgets.friends.WidgetFriendsListAdapter

@AliucordPlugin
class HideAvatars : Plugin() {

    override fun start(context: Context) {
        
        tryPatch("WidgetChatListAdapterItemMessage") {
            patcher.after<WidgetChatListAdapterItemMessage>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetChatListAdapterItemEphemeralMessage") {
            patcher.after<WidgetChatListAdapterItemEphemeralMessage>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetHomeHeaderManager") {
            patcher.after<WidgetHomeHeaderManager>(
                "onViewBound", View::class.java
            ) {
                val view = it.args[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("UserProfileHeaderView") {
            patcher.after<UserProfileHeaderView>(
                "updateAvatar", String::class.java, Boolean::class.java
            ) {
                (this as? ViewGroup)?.let { vg -> hideAllDraweeViews(vg) }
            }
        }

        tryPatch("WidgetUserSheet") {
            patcher.after<WidgetUserSheet>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
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
                val holder = it.args[0] as? RecyclerView.ViewHolder ?: return@after
                val root = holder.itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()

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
