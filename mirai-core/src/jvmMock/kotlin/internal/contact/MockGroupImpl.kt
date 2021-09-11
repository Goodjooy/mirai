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
import net.mamoe.mirai.data.MemberInfo
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.internal.contact.uin
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.mock.contact.MockAnonymousMember
import net.mamoe.mirai.mock.contact.MockGroup
import net.mamoe.mirai.mock.contact.MockNormalMember
import net.mamoe.mirai.mock.contact.announcement.MockAnnouncements
import net.mamoe.mirai.mock.internal.msgsrc.OnlineMsgSrcToGroup
import net.mamoe.mirai.mock.internal.msgsrc.newMsgSrc
import net.mamoe.mirai.mock.internal.remotefile.MockRemoteFileRoot
import net.mamoe.mirai.mock.utils.broadcastBlocking
import net.mamoe.mirai.mock.utils.mock
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.RemoteFile
import net.mamoe.mirai.utils.cast
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

internal class MockGroupImpl(
    parentCoroutineContext: CoroutineContext,
    bot: MockBot,
    id: Long,
    override var uin: Long,
    name: String,
) : AbstractMockContact(
    parentCoroutineContext, bot, id
), MockGroup {

    override fun addMember(mockMember: MemberInfo): MockGroup {
        addMember0(mockMember)
        return this
    }

    override fun addMember0(mockMember: MemberInfo): MockNormalMember {
        val nMember = MockNormalMemberImpl(
            this.coroutineContext,
            bot,
            mockMember.uin,
            this,
            mockMember.permission,
            mockMember.remark,
            mockMember.nick,
            mockMember.muteTimestamp,
            mockMember.joinTimestamp,
            mockMember.lastSpeakTimestamp,
            mockMember.specialTitle,
            mockMember.nameCard
        )

        if (nMember.id == bot.id) {
            botAsMember = nMember
        } else {
            members.delegate.removeIf { it.uin == nMember.id }
            members.delegate.add(nMember)
        }

        if (nMember.permission == MemberPermission.OWNER) {
            if (::owner.isInitialized) {
                owner.mock().permission = MemberPermission.MEMBER
            }
            owner = nMember
        }
        return nMember
    }

    override suspend fun changeOwner(member: NormalMember) {
        val oldOwner = owner
        val oldPerm = member.permission
        member.mock().permission = MemberPermission.OWNER
        oldOwner.mock().permission = MemberPermission.MEMBER
        owner = member

        if (member === botAsMember) {
            BotGroupPermissionChangeEvent(this, oldPerm, MemberPermission.OWNER)
        } else {
            MemberPermissionChangeEvent(member, oldPerm, MemberPermission.OWNER)
        }.broadcast()

        if (oldOwner === botAsMember) {
            BotGroupPermissionChangeEvent(this, MemberPermission.OWNER, MemberPermission.MEMBER)
        } else {
            MemberPermissionChangeEvent(oldOwner, MemberPermission.OWNER, MemberPermission.MEMBER)
        }.broadcast()
    }

    override fun changeOwnerNoEventBroadcast(member: NormalMember) {
        val oldOwner = owner
        member.mock().permission = MemberPermission.OWNER
        oldOwner.permission = MemberPermission.MEMBER
        owner = member
    }

    override fun newAnonymous(nick: String, id: String): MockAnonymousMember {
        return MockAnonymousMemberImpl(
            coroutineContext, bot, 80000000, id, this, nick
        )
    }

    override var name: String = name
        set(value) {
            val ov = field
            if (ov == value) return
            field = value
            GroupNameChangeEvent(ov, value, this@MockGroupImpl, botAsMember).broadcastBlocking()
        }

    override lateinit var owner: MockNormalMember
    override lateinit var botAsMember: MockNormalMember
    override val members: ContactList<MockNormalMember> = ContactList()
    override fun get(id: Long): MockNormalMember? {
        if (id == bot.id) return botAsMember
        return members[id]
    }

    override fun contains(id: Long): Boolean = members.any { it.id == id }


    override suspend fun quit(): Boolean {
        return if (bot.groups.delegate.remove(this)) {
            BotLeaveEvent.Active(this).broadcast()
            cancel(CancellationException("Bot quited group $id"))
            true
        } else {
            false
        }
    }

    override val announcements: MockAnnouncements = MockAnnouncementsImpl(this)

    @Suppress("OverridingDeprecatedMember")
    override val settings: GroupSettings = object : GroupSettings {
        override var entranceAnnouncement: String
            get() = ""
            set(value) {}

        override var isMuteAll: Boolean = false
            set(value) {
                val ov = field
                if (ov == value) return
                field = value
                GroupMuteAllEvent(ov, value, this@MockGroupImpl, botAsMember).broadcastBlocking()
            }
        override var isAllowMemberInvite: Boolean = false
            set(value) {
                val ov = field
                if (ov == value) return
                field = value
                GroupAllowMemberInviteEvent(ov, value, this@MockGroupImpl, botAsMember).broadcastBlocking()
            }

        @MiraiExperimentalApi
        override val isAutoApproveEnabled: Boolean
            get() = false

        override val isAnonymousChatEnabled: Boolean
            get() = false
    }


    override fun newMessagePreSend(message: Message): MessagePreSendEvent =
        GroupMessagePreSendEvent(this, message)


    override suspend fun postMessagePreSend(message: MessageChain, receipt: MessageReceipt<*>) {
        GroupMessagePostSendEvent(this, message, null, receipt = receipt.cast())
            .broadcast()
    }

    override fun newMessageSource(message: MessageChain): OnlineMessageSource.Outgoing {
        return newMsgSrc(false) { ids, internalIds, time ->
            OnlineMsgSrcToGroup(ids, internalIds, time, message, bot, bot, this)
        }
    }

    override suspend fun sendMessage(message: Message): MessageReceipt<Group> {
        return super<AbstractMockContact>.sendMessage(message).cast()
    }

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override suspend fun uploadVoice(resource: ExternalResource): Voice =
        resource.mockUploadVoice(bot)

    override suspend fun setEssenceMessage(source: MessageSource): Boolean {
        return true
    }

    override val filesRoot: RemoteFile by lazy {
        MockRemoteFileRoot(this)
    }

    override suspend fun uploadAudio(resource: ExternalResource): OfflineAudio =
        resource.mockUploadAudio(bot)

    override fun toString(): String {
        return "$name($id)"
    }
}