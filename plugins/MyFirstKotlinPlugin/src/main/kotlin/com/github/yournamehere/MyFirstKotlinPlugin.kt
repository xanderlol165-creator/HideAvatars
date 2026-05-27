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

// Tier 1 — confirmed common classes
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemEphemeralMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMember
import com.discord.widgets.home.WidgetHomeHeaderManager
import com.discord.widgets.user.profile.UserProfileHeaderView
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.discord.widgets.guilds.list.WidgetGuildListItem
import com.discord.widgets.channels.privateChannels.PrivateChannelListItem

// Tier 2 — likely exist but verify if build fails
import com.discord.widgets.friends.WidgetFriendsListAdapter
import com.discord.widgets.channels.list.WidgetChannelListItemVoiceUser
import com.discord.widgets.reactions.WidgetReactionsList

@AliucordPlugin
class HideAvatars : Plugin() {

    override fun start(context: Context) {

        // ── TIER 1: High confidence these stubs exist ─────────────────────────
        // If your build still fails, comment out Tier 2 first before touching
        // anything here — these are the most commonly referenced classes across
        // real Aliucord plugins on GitHub.

        // Chat message rows
        tryPatch("WidgetChatListAdapterItemMessage") {
            patcher.after<WidgetChatListAdapterItemMessage>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        // Ephemeral messages ("only you can see this")
        tryPatch("WidgetChatListAdapterItemEphemeralMessage") {
            patcher.after<WidgetChatListAdapterItemEphemeralMessage>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        // Members sidebar (avatars next to every name in a server)
        tryPatch("WidgetChatListAdapterItemMember") {
            patcher.after<WidgetChatListAdapterItemMember>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        // Top-left home header avatar (your own avatar)
        tryPatch("WidgetHomeHeaderManager") {
            patcher.after<WidgetHomeHeaderManager>(
                "onViewBound", View::class.java
            ) {
                val view = it.args[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        // Full profile / DM header avatar
        tryPatch("UserProfileHeaderView") {
            patcher.after<UserProfileHeaderView>(
                "updateAvatar", String::class.java, Boolean::class.java
            ) {
                (this as? ViewGroup)?.let { vg -> hideAllDraweeViews(vg) }
            }
        }

        // User bottom sheet (tap on any username)
        tryPatch("WidgetUserSheet") {
            patcher.after<WidgetUserSheet>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
                val view = it.args[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        // Server/guild icon list on the left rail
        tryPatch("WidgetGuildListItem") {
            patcher.after<WidgetGuildListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        // DM list sidebar (each DM row shows the other user's avatar)
        tryPatch("PrivateChannelListItem") {
            patcher.after<PrivateChannelListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        // ── TIER 2: Likely exist, but comment these out first if build fails ──
        // Re-enable one at a time and rebuild after each to isolate any
        // missing stub. These three cover friends, voice, and reactions —
        // the next most visible surfaces after Tier 1.

        // Friends list rows (adapter-level fallback, safer than item class)
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

        // Voice channel user tiles in the channel list
        tryPatch("WidgetChannelListItemVoiceUser") {
            patcher.after<WidgetChannelListItemVoiceUser>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        // "Who reacted" bottom sheet
        tryPatch("WidgetReactionsList") {
            patcher.after<WidgetReactionsList>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
                val view = it.args[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()

    // Silently skips any patch whose class or method is missing in this build.
    // Check logcat for "HideAvatars: skipping" to see what didn't apply.
    private fun tryPatch(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: ClassNotFoundException) {
            logger.warn("HideAvatars: skipping $name — class not found in this build")
        } catch (e: NoSuchMethodException) {
            logger.warn("HideAvatars: skipping $name — method not found in this build")
        } catch (e: Exception) {
            logger.error("HideAvatars: unexpected error patching $name", e)
        }
    }

    // Recursively walks the view tree and hides every SimpleDraweeView.
    // INVISIBLE keeps layout spacing intact — swap to GONE if you want
    // the space to collapse entirely.
    private fun hideAllDraweeViews(root: ViewGroup) {
        for (i in 0 until root.childCount) {
            when (val child = root.getChildAt(i)) {
                is SimpleDraweeView -> child.visibility = View.INVISIBLE
                is ViewGroup        -> hideAllDraweeViews(child)
            }
        }
    }
}
