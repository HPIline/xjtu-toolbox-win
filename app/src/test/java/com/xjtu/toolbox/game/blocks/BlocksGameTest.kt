package com.xjtu.toolbox.game.blocks

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocksGameTest {

    private fun game(mode: BlocksMode = BlocksMode.MARATHON) = BlocksGame(mode, Random(42))

    private fun BlocksGame.fillRow(y: Int, vararg holes: Int) {
        for (x in 0 until BOARD_WIDTH) cells[y * BOARD_WIDTH + x] = if (x in holes) 0 else GARBAGE
    }

    private val bottom = BOARD_HEIGHT - 1

    @Test
    fun `前七块正好是一整包`() {
        val g = game()
        val firstBag = listOf(g.piece!!.type) + g.preview(6)
        assertEquals((0 until PIECE_COUNT).toList(), firstBag.sorted())
    }

    @Test
    fun `横放长条补满一行就消掉`() {
        val g = game()
        g.fillRow(bottom, 6, 7, 8, 9)
        g.piece = ActivePiece(type = 0, rot = 0, x = 6, y = 5)

        g.hardDrop()

        assertEquals(1, g.lines)
        assertTrue((0 until BOARD_WIDTH).all { g.cell(it, bottom) == 0 })
        assertEquals("单消", g.clearEvent?.label)
    }

    @Test
    fun `连续两次四消算背靠背`() {
        val g = game()
        repeat(2) { round ->
            for (y in bottom - 3..bottom) g.fillRow(y, 9)
            // 竖放长条占包围盒第 2 列，所以盒子放在 x = 7
            g.piece = ActivePiece(type = 0, rot = 1, x = 7, y = 2)
            g.hardDrop()
            assertEquals(4 * (round + 1), g.lines)
        }
        assertTrue(g.clearEvent!!.label.startsWith("四消"))
        assertTrue(g.clearEvent!!.label.contains("背靠背"))
    }

    @Test
    fun `贴左墙的竖长条转平会被踢出来`() {
        val g = game()
        g.piece = ActivePiece(type = 0, rot = 1, x = -2, y = 8)
        assertTrue(g.rotate(clockwise = true))
        val cells = g.piece!!.cells()
        assertTrue((0 until 8 step 2).all { cells[it] >= 0 })
    }

    @Test
    fun `转进三角槽算 T-Spin 双消`() {
        val g = game()
        g.fillRow(bottom, 4)
        g.fillRow(bottom - 1, 3, 4, 5)
        g.cells[(bottom - 2) * BOARD_WIDTH + 3] = GARBAGE // 槽口上方的屋檐
        g.piece = ActivePiece(type = 2, rot = 1, x = 3, y = bottom - 2)

        assertTrue(g.rotate(clockwise = true))
        g.hardDrop()

        assertEquals(2, g.lines)
        assertEquals(1200, g.score)
        assertTrue(g.clearEvent!!.label.startsWith("T-Spin 双消"))
    }

    @Test
    fun `落地后过了锁定延迟才锁住`() {
        val g = game()
        g.piece = ActivePiece(type = 1, rot = 0, x = 3, y = bottom - 1)
        g.tick(LOCK_DELAY_MS - 1)
        assertEquals(0, g.lockCount)
        g.tick(2)
        assertEquals(1, g.lockCount)
    }

    @Test
    fun `抬升时底部冒出只有一个缺口的垃圾行`() {
        val g = game(BlocksMode.RISE)
        g.riseGarbage()
        assertEquals(BOARD_WIDTH - 1, (0 until BOARD_WIDTH).count { g.cell(it, bottom) == GARBAGE })
        assertFalse(g.over)
    }

    @Test
    fun `顶行有块时再抬升就结束`() {
        val g = game(BlocksMode.RISE)
        g.cells[0] = GARBAGE
        g.riseGarbage()
        assertTrue(g.over)
    }

    @Test
    fun `冲分两分钟一到就结束`() {
        val g = game(BlocksMode.ULTRA)
        g.tick(ULTRA_MS)
        assertTrue(g.over)
        assertTrue(g.timeUp)
    }
}
