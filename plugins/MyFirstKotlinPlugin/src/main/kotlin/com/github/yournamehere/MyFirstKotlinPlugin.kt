package com.aliucord.plugins

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.facebook.drawee.view.SimpleDraweeView

// Chat & messages
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMember
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemReaction
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemEphemeralMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemTyping

// Reactions sheet
import com.discord.widgets.reactions.WidgetReactionsList
import com.discord.widgets.reactions.ReactionsBottomSheet

// Home / header
import com.discord.widgets.home.WidgetHomeHeaderManager

// User & member profiles
import com.discord.widgets.user.profile.UserProfileHeaderView
import com.discord.widgets.user.usersheet.WidgetUserSheet

// Friends list
import com.discord.widgets.friends.WidgetFriendsListAdapter
import com.discord.widgets.friends.WidgetFriendsListItem

// DM list
import com.discord.widgets.channels.privateChannels.PrivateChannelListItem

// Voice channel participants
import com.discord.widgets.voice.fullscreen.WidgetVoiceFullscreenParticipant
import com.discord.widgets.channels.list.WidgetChannelListItemVoiceUser

// Guild / server list
import com.discord.widgets.guilds.list.WidgetGuildListItem

// Search results
import com.discord.widgets.search.WidgetSearchResultsAdapter
import com.discord.widgets.search.WidgetSearchResultsListItem

// Mention / autocomplete
import com.discord.widgets.chat.input.autocomplete.MentionSuggestionItem
import com.discord.widgets.chat.input.autocomplete.WidgetChatInputAutocompleteAdapter

// Threads
import com.discord.widgets.threads.WidgetThreadListItem

// Forum posts
import com.discord.widgets.forums.WidgetForumPostListItem

// Notifications
import com.discord.widgets.notifications.WidgetNotificationListItem

// Stage channel
import com.discord.widgets.stage.StageParticipantListItem

// Invite screen
import com.discord.widgets.invite.WidgetInviteInviter

// Audit log
import com.discord.widgets.settings.guild.audit.WidgetAuditLogItem

@AliucordPlugin
class HideAvatars : Plugin() {

    override fun start() {
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

        tryPatch("WidgetChatListAdapterItemReaction") {
            patcher.after<WidgetChatListAdapterItemReaction>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetChatListAdapterItemTyping") {
            patcher.after<WidgetChatListAdapterItemTyping>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetThreadListItem") {
            patcher.after<WidgetThreadListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetForumPostListItem") {
            patcher.after<WidgetForumPostListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetReactionsList") {
            patcher.after<WidgetReactionsList>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
                val view = it[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("ReactionsBottomSheet") {
            patcher.after<ReactionsBottomSheet>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
                val view = it[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("WidgetHomeHeaderManager") {
            patcher.after<WidgetHomeHeaderManager>(
                "onViewBound", View::class.java
            ) {
                val view = it[0] as? ViewGroup ?: return@after
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
                val view = it[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("WidgetChatListAdapterItemMember") {
            patcher.after<WidgetChatListAdapterItemMember>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetFriendsListItem") {
            patcher.after<WidgetFriendsListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetFriendsListAdapter") {
            patcher.after<WidgetFriendsListAdapter>(
                "onBindViewHolder",
                RecyclerView.ViewHolder::class.java,
                Int::class.java
            ) {
                val holder = it[0] as? RecyclerView.ViewHolder ?: return@after
                val root = holder.itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("PrivateChannelListItem") {
            patcher.after<PrivateChannelListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetChannelListItemVoiceUser") {
            patcher.after<WidgetChannelListItemVoiceUser>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetVoiceFullscreenParticipant") {
            patcher.after<WidgetVoiceFullscreenParticipant>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetGuildListItem") {
            patcher.after<WidgetGuildListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetSearchResultsListItem") {
            patcher.after<WidgetSearchResultsListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetSearchResultsAdapter") {
            patcher.after<WidgetSearchResultsAdapter>(
                "onBindViewHolder",
                RecyclerView.ViewHolder::class.java,
                Int::class.java
            ) {
                val holder = it[0] as? RecyclerView.ViewHolder ?: return@after
                val root = holder.itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("MentionSuggestionItem") {
            patcher.after<MentionSuggestionItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetChatInputAutocompleteAdapter") {
            patcher.after<WidgetChatInputAutocompleteAdapter>(
                "onBindViewHolder",
                RecyclerView.ViewHolder::class.java,
                Int::class.java
            ) {
                val holder = it[0] as? RecyclerView.ViewHolder ?: return@after
                val root = holder.itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("StageParticipantListItem") {
            patcher.after<StageParticipantListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetInviteInviter") {
            patcher.after<WidgetInviteInviter>(
                "onViewCreated", View::class.java, Bundle::class.java
            ) {
                val view = it[0] as? ViewGroup ?: return@after
                hideAllDraweeViews(view)
            }
        }

        tryPatch("WidgetAuditLogItem") {
            patcher.after<WidgetAuditLogItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }

        tryPatch("WidgetNotificationListItem") {
            patcher.after<WidgetNotificationListItem>(
                "onConfigure", Int::class.java, Any::class.java
            ) {
                val root = itemView as? ViewGroup ?: return@after
                hideAllDraweeViews(root)
            }
        }
    }

    override fun stop() = patcher.unpatchAll()

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

    private fun hideAllDraweeViews(root: ViewGroup) {
        for (i in 0 until root.childCount) {
            when (val child = root.getChildAt(i)) {
                is SimpleDraweeView -> child.visibility = View.INVISIBLE
                is ViewGroup        -> hideAllDraweeViews(child)
            }
        }
    }
}
