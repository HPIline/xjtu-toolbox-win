package com.xjtu.toolbox.schedule

import com.xjtu.toolbox.util.AppJson
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleChangeEventTest {

    /**
     * 在别的类里按类型取序列化器（encodeToString<List<ScheduleChangeEvent>>）要读它的伴生对象。
     * 伴生对象被写成 private 时这里直接 IllegalAccessError——5.0.8 保存调课记录时就是这么崩的。
     */
    @Test
    fun `别的类里能序列化再读回来`() {
        val events = listOf(
            ScheduleChangeEvent(courseName = "高数", kind = ScheduleChangeEvent.Kind.CANCELLED, fromDay = 1, fromStartSection = 1, fromEndSection = 2, weeks = listOf(4)),
        )
        val json = AppJson.encodeToString(events)
        assertEquals(events, AppJson.decodeFromString<List<ScheduleChangeEvent>>(json))
    }
}
