/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.mock.test

import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.mock.MockBotFactory
import net.mamoe.mirai.mock.addGroup
import net.mamoe.mirai.mock.contact.addMember
import net.mamoe.mirai.mock.utils.MockActions.sayMessage
import net.mamoe.mirai.mock.utils.group
import net.mamoe.mirai.mock.utils.member
import net.mamoe.mirai.mock.utils.mockUploadAsOnlineAudio
import net.mamoe.mirai.utils.ExternalResource.Companion.toAutoCloseable
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File


/*
 * This file only for showing MockDSL and how to use mock bot.
 * Not included in testing running
 */

@Suppress("unused")
internal suspend fun dslTest() {
    val bot = MockBotFactory.newMockBotBuilder().create()

    bot.addGroup(1, "") {
        addMember(541, "Dmo") {
            permission(MemberPermission.OWNER)
        }
    }

    bot.group(50).member(70) says "0"
    bot.group(1).member(1) sayMessage {
        File("helloworld.amr").toExternalResource().toAutoCloseable().mockUploadAsOnlineAudio(bot)
    }

}
