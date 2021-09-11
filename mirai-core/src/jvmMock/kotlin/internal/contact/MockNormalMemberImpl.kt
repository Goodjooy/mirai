/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.mock.internal.contact

import kotlinx.coroutines.cancel
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.OnlineMessageSource
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.mock.contact.MockGroup
import net.mamoe.mirai.mock.contact.MockNormalMember
import net.mamoe.mirai.mock.internal.msgsrc.OnlineMsgSrcFromGroup
import net.mamoe.mirai.mock.internal.msgsrc.OnlineMsgSrcToTemp
import net.mamoe.mirai.mock.internal.msgsrc.newMsgSrc
import net.mamoe.mirai.mock.utils.broadcastBlocking
import net.mamoe.mirai.utils.cast
import net.mamoe.mirai.utils.currentTimeSeconds
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

internal class MockNormalMemberImpl(
    parentCoroutineContext: CoroutineContext,
    bot: MockBot,
    id: Long,
    override val group: MockGroup,
    override var permission: MemberPermission,
    override var remark: String,
    nick: String,
    override var muteTimeRemaining: Int,
    override var joinTimestamp: Int,
    override var lastSpeakTimestamp: Int,
    specialTitle: String,
    nameCard: String,
) : AbstractMockContact(
    parentCoroutineContext, bot,
    id
), MockNormalMember {

    private var _specialTitle: String = specialTitle
    private var _nameCard: String = nameCard

    override var nameCard: String
        get() = _nameCard
        set(value) {
            if (!group.botPermission.isOperator()) {
                throw PermissionDeniedException("Bot don't have permission to change the namecard of $this")
            }
            MemberCardChangeEvent(_nameCard, value, this).broadcastBlocking()
            _nameCard = value
        }
    override var specialTitle: String
        get() = _specialTitle
        set(value) {
            if (group.botPermission != MemberPermission.OWNER) {
                throw PermissionDeniedException("Bot is not the owner of $group so bot cannot change the specialTitle of $this")
            }
            MemberSpecialTitleChangeEvent(_specialTitle, value, this, group.botAsMember).broadcastBlocking()
            _specialTitle = value
        }

    override var nick: String = nick
        set(value) {
            val friend0 = bot.getFriend(id)
            if (friend0 != null) {
                friend0.nick = value
            } else {
                field = value
            }
        }

    override fun setNameCardNoEventBroadcast(value: String) {
        _nameCard = value
    }

    override fun setSpecialTitleNoEventBroadcast(value: String) {
        _specialTitle = value
    }

    override suspend fun unmute() {
        requireBotPermissionHigherThanThis("unmute")
        muteTimeRemaining = 0
    }

    override suspend fun kick(message: String, block: Boolean) {
        kick(message)
    }

    override suspend fun kick(message: String) {
        requireBotPermissionHigherThanThis("kick")
        if (group.members.delegate.remove(this)) {
            MemberLeaveEvent.Kick(this, group.botAsMember).broadcastBlocking()
            cancel(CancellationException("Member kicked: $message"))
        }
    }

    override suspend fun modifyAdmin(operation: Boolean) {
        if (group.botPermission != MemberPermission.OWNER) {
            throw PermissionDeniedException("Bot is not the owner of group ${group.id}, can't modify the permission of $id($permission")
        }
        if (operation && permission > MemberPermission.MEMBER) return

        if (permission == MemberPermission.OWNER) {
            throw IllegalArgumentException("Not allowed modify permission of owner ($id, $permission)")
        }
        val newPerm = if (operation) MemberPermission.ADMINISTRATOR else MemberPermission.MEMBER
        if (newPerm != permission) {
            val oldPerm = permission
            permission = oldPerm
            MemberPermissionChangeEvent(this, oldPerm, newPerm).broadcast()
        }
    }

    override suspend fun sendMessage(message: Message): MessageReceipt<NormalMember> {
        return super<AbstractMockContact>.sendMessage(message).cast()
    }

    override suspend fun mute(durationSeconds: Int) {
        requireBotPermissionHigherThanThis("mute")
        require(durationSeconds > 0) {
            "$durationSeconds < 0"
        }
        muteTimeRemaining = currentTimeSeconds().toInt() + durationSeconds
    }

    override suspend fun says(message: MessageChain): MessageChain {
        val src = newMsgSrc(true) { ids, internalIds, time ->
            OnlineMsgSrcFromGroup(ids, internalIds, time, message, bot, this)
        }
        val msg = src withMessage message
        GroupMessageEvent(nameCardOrNick, permission, this, msg, src.time).broadcast()
        return msg
    }

    override fun newMessagePreSend(message: Message): MessagePreSendEvent {
        return GroupTempMessagePreSendEvent(this, message)
    }

    override suspend fun postMessagePreSend(message: MessageChain, receipt: MessageReceipt<*>) {
        GroupTempMessagePostSendEvent(this, message, null, receipt.cast()).broadcast()
    }

    override fun newMessageSource(message: MessageChain): OnlineMessageSource.Outgoing {
        return newMsgSrc(false) { ids, internalIds, time ->
            OnlineMsgSrcToTemp(ids, internalIds, time, message, bot, bot, this)
        }
    }

    override fun toString(): String {
        return "$nameCardOrNick[$id]"
    }
}