package com.xjtu.toolbox.coupon

import com.xjtu.toolbox.util.safeParseJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponJsonParserTest {
    @Test
    fun parseEmptyPage() {
        val page = CouponJsonParser.parsePage(
            """{"code":200,"msg":"操作成功","data":{"records":[],"total":0}}""".safeParseJsonObject()
        )

        assertEquals(0, page.total)
        assertTrue(page.records.isEmpty())
    }

    @Test
    fun parseCouponPageNormalizesMoneyAndImageUrl() {
        val page = CouponJsonParser.parsePage(
            """
            {
              "code": 200,
              "data": {
                "records": [
                  {
                    "sendId": "2821777",
                    "showCardId": "F14025FB-C3B9-438C-A356-C4C97802E39B",
                    "startDate": "2026-04-04",
                    "endDate": "2026-04-14 23:59",
                    "pic": "/voucher-bucket/2026/04/04/banner.png",
                    "tranamt": "500",
                    "ltranamt": "500",
                    "voucherName": "校庆加餐券",
                    "typeName": "加餐券",
                    "lknumber": "1"
                  }
                ],
                "total": 1
              }
            }
            """.trimIndent().safeParseJsonObject()
        )

        val coupon = page.records.single()
        assertEquals(1, page.total)
        assertEquals("校庆加餐券", coupon.voucherName)
        assertEquals(500L, coupon.amountFen)
        assertEquals(500L, coupon.leftAmountFen)
        assertEquals(5.0, coupon.leftAmountYuan, 0.001)
        assertEquals(1, coupon.leftCount)
        assertEquals("https://egc.xjtu.edu.cn/voucher-bucket/2026/04/04/banner.png", coupon.imageUrl)
    }

    @Test
    fun parseUsedUpCouponKeepsZeroBalance() {
        val page = CouponJsonParser.parsePage(
            """
            {
              "code": 200,
              "data": {
                "records": [
                  {
                    "sendId": "2551029",
                    "showCardId": "0E2E40A2-D301-45F2-A626-A94D8288F0F2",
                    "tranamt": "500",
                    "ltranamt": "0",
                    "voucherName": "国庆加餐券",
                    "typeName": "加餐券",
                    "lknumber": "0"
                  }
                ],
                "total": 8
              }
            }
            """.trimIndent().safeParseJsonObject()
        )

        val coupon = page.records.single()
        assertEquals(0L, coupon.leftAmountFen)
        assertEquals(0, coupon.leftCount)
        assertEquals(8, page.total)
    }

    @Test
    fun filterWireValuesMatchCapturedRequests() {
        assertEquals("0", CouponFilter.AVAILABLE.status)
        assertEquals("", CouponFilter.AVAILABLE.count)
        assertEquals("3", CouponFilter.AVAILABLE.expired)
        assertEquals("1", CouponFilter.USABLE.status)
        assertEquals("1", CouponFilter.USABLE.count)
        assertEquals("3", CouponFilter.USABLE.expired)
        assertEquals("", CouponFilter.USED_UP.status)
        assertEquals("0", CouponFilter.USED_UP.count)
        assertEquals("", CouponFilter.USED_UP.expired)
        assertEquals("", CouponFilter.EXPIRED.status)
        assertEquals("1", CouponFilter.EXPIRED.count)
        assertEquals("2", CouponFilter.EXPIRED.expired)
    }
}
