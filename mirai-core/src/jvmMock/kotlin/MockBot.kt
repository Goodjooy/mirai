/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.mamoe.mirai.mock

import net.mamoe.kjbb.JvmBlockingBridge
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.ContactList
import net.mamoe.mirai.message.data.OnlineAudio
import net.mamoe.mirai.mock.contact.*
import net.mamoe.mirai.mock.database.MessageDatabase
import net.mamoe.mirai.mock.fsserver.TmpFsServer
import net.mamoe.mirai.mock.userprofile.UserProfileService
import net.mamoe.mirai.mock.utils.NameGenerator
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.JavaFriendlyAPI
import net.mamoe.mirai.utils.cast
import java.util.function.Consumer
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.internal.LowPriorityInOverloadResolution

@Suppress("unused")
@JvmBlockingBridge
public interface MockBot : Bot, MockContactOrBot, MockUserOrBot {
    override val bot: MockBot get() = this

    /// Contract API override
    @MockBotDSL
    override fun getFriend(id: Long): MockFriend? = super.getFriend(id)?.cast()

    @MockBotDSL
    override fun getFriendOrFail(id: Long): MockFriend = super.getFriendOrFail(id).cast()

    @MockBotDSL
    override fun getGroup(id: Long): MockGroup? = super.getGroup(id)?.cast()

    @MockBotDSL
    override fun getGroupOrFail(id: Long): MockGroup = super.getGroupOrFail(id).cast()

    @MockBotDSL
    override fun getStranger(id: Long): MockStranger? = super.getStranger(id)?.cast()

    @MockBotDSL
    override fun getStrangerOrFail(id: Long): MockStranger = super.getStrangerOrFail(id).cast()

    override val groups: ContactList<MockGroup>
    override val friends: ContactList<MockFriend>
    override val strangers: ContactList<MockStranger>
    override val otherClients: ContactList<MockOtherClient>
    override val asFriend: MockFriend
    override val asStranger: MockStranger

    /// All mock api will not broadcast event

    public val nameGenerator: NameGenerator
    public val tmpFsServer: TmpFsServer
    public val msgDatabase: MessageDatabase
    public val userProfileService: UserProfileService

    /// Mock Contact API

    @MockBotDSL
    public fun addGroup(id: Long, name: String): MockGroup

    @MockBotDSL
    public fun addGroup(id: Long, uin: Long, name: String): MockGroup

    @MockBotDSL
    @JavaFriendlyAPI
    @LowPriorityInOverloadResolution
    public fun addGroup(id: Long, name: String, action: Consumer<MockGroup>): MockBot {
        action.accept(addGroup(id, name))
        return this
    }

    @MockBotDSL
    public fun addFriend(id: Long, name: String): MockFriend

    @MockBotDSL
    public fun addStranger(id: Long, name: String): MockStranger

    @MockBotDSL
    public suspend fun uploadOnlineAudio(resource: ExternalResource): OnlineAudio
}

@MockBotDSL
public inline fun MockBot.addGroup(id: Long, name: String, action: MockGroup.() -> Unit): MockBot {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return apply {
        addGroup(id, name).also(action)
    }
}
