package com.fc.v2.heri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THeriCusBlock;
import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.custom.CusBookReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报关行导表「整串进门」规矩集成测试（H2 + 真实 mapper）。
 *
 * @author fuce
 * @date 2026-10-07
 */
class THeriCusBookServiceTests extends CusBookITSupport {

    private static final String OP = "forwarder_chen";

    @BeforeEach
    void init() throws Exception {
        setUp();
    }

    // ---------------- 进门的样子：串头行数 ----------------

    @Test
    @DisplayName("串头写着几行就只当几行，原串报的行数落在串头上，隔年查的还是这个数")
    void admit_declaredRowsPersisted() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(99, "BL1", 10, 10, "20GP"),
                row(88, "BL2", 20, 20, "40GP")), OP);

        THeriCusBook head = cusBookService.selectByBookNo(rc.getBookNo());
        assertEquals(3, head.getDeclaredRows(), "串头写 3 就是 3，不因实到 2 行改写");
        assertEquals(0, head.getStatus(), "没到齐=在办，等续办补齐");
        assertEquals(2, head.getLastRowNo());

        // 续办之后再查：当初报的行数仍不改
        cusBookService.readmit(rc.getBookNo(), rows(row(null, "BL3", 30, 30, "40HQ")), OP);
        assertEquals(3, cusBookService.selectByBookNo(rc.getBookNo()).getDeclaredRows());
    }

    @Test
    @DisplayName("串头那格空着按 150 行算")
    void admit_blankHeader_defaults150() {
        for (String blank : new String[]{null, "", "   "}) {
            CusBookReceipt rc = cusBookService.admit(blank, rows(row(null, "BL1", 1, 1, "20GP")), OP);
            assertEquals(150, rc.getDeclaredRows());
            assertEquals(150, cusBookService.selectByBookNo(rc.getBookNo()).getDeclaredRows());
        }
    }

    @Test
    @DisplayName("串头写的不是正整数：整串不收，册上一笔都不落")
    void admit_badHeader_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> cusBookService.admit("abc", rows(row(null, "BL1", 1, 1, "20GP")), OP));
        assertThrows(IllegalArgumentException.class,
                () -> cusBookService.admit("0", rows(row(null, "BL1", 1, 1, "20GP")), OP));
        assertThrows(IllegalArgumentException.class,
                () -> cusBookService.admit("-3", rows(row(null, "BL1", 1, 1, "20GP")), OP));
        assertEquals(Integer.valueOf(0), cusBookMapper.selectCount(null));
        assertEquals(Integer.valueOf(0), cusRowMapper.selectCount(null));
    }

    @Test
    @DisplayName("串头数字之外再多的部分另开一串，不就地往下接")
    void admit_overflow_opensAnotherBook() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 20, 20, "20GP"),
                row(null, "BL3", 30, 30, "40HQ"),
                row(null, "BL4", 40, 40, "45HQ")), OP);

        assertEquals(2, cusBookService.listRows(rc.getBookNo()).size(), "原串只接串头那 2 行");
        assertNotNull(rc.getOverflowBookNo(), "多出的 2 行另开一串");
        CusBookReceipt over = rc.getOverflow();
        assertEquals(rc.getOverflowBookNo(), over.getBookNo());
        assertEquals(2, over.getReceivedRows(), "溢出那串按它自己的行数记串头");
        assertEquals(2, cusBookService.selectByBookNo(over.getBookNo()).getDeclaredRows());
        List<THeriCusRow> overRows = cusBookService.listRows(over.getBookNo());
        assertEquals("BL3", overRows.get(0).getBlNo());
        assertEquals("BL4", overRows.get(1).getBlNo());
        // 行不飘串：溢出的行不在原串
        assertNull(cusBookService.findRow(rc.getBookNo(), 3));
    }

    // ---------------- 解析一趟：箱型对着校验档逐行核 ----------------

    @Test
    @DisplayName("箱型不合的行拦在门外：不落册、不占行次、不进三笔，原样留门口明细")
    void door_badKind_blocked_notLanded() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 10, 10, "77ZZ"),   // 校验档查无
                row(null, "BL3", 10, 10, "99XX")), OP); // 撤下档

        assertEquals(1, rc.getReceivedRows(), "进门只有箱型合的那 1 行");
        assertEquals(2, rc.getBlockedRows(), "两笔拦回不进三笔");
        assertEquals(1, rc.getSignedRows());
        assertEquals(0, rc.getRejectedRows());
        assertEquals(1 + 2, rc.getReceivedRows() + rc.getBlockedRows());

        // 册上只有 BL1，行次只排给进门的行；拦回的从不在册上落过（不是先落再撤）
        List<THeriCusRow> landed = cusBookService.listRows(rc.getBookNo());
        assertEquals(1, landed.size());
        assertEquals("BL1", landed.get(0).getBlNo());
        assertEquals(Integer.valueOf(1), landed.get(0).getRowNo());
        assertEquals(Integer.valueOf(0), cusRowMapper.selectCount(new QueryWrapper<THeriCusRow>()
                .in("bl_no", "BL2", "BL3")));

        List<THeriCusBlock> blocks = cusBookService.listBlocked(rc.getBookNo());
        assertEquals(2, blocks.size());
        assertEquals("77ZZ", blocks.get(0).getDeviceKind());
        assertTrue(blocks.get(0).getReason().contains("箱型不合"));
        assertTrue(blocks.get(1).getReason().contains("箱型不合"));
        assertEquals(2, cusBookService.selectByBookNo(rc.getBookNo()).getBlockedRows());
    }

    @Test
    @DisplayName("箱型那格空着按没写拦，不当都可以")
    void door_blankKind_treatedAsMissing() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, null),
                row(null, "BL2", 10, 10, "  ")), OP);

        assertEquals(0, rc.getReceivedRows());
        assertEquals(2, rc.getBlockedRows());
        for (THeriCusBlock b : cusBookService.listBlocked(rc.getBookNo())) {
            assertTrue(b.getReason().contains("箱型没写"));
            assertNull(b.getDeviceKind(), "原样留着：空就是空");
        }
        assertEquals(0, cusBookService.listRows(rc.getBookNo()).size());
        assertEquals(0, cusBookService.selectByBookNo(rc.getBookNo()).getStatus(), "没一行进门，仍在办");
    }

    // ---------------- 两个件数都得有，不等就退并点名差几条 ----------------

    @Test
    @DisplayName("申报件数/实签件数缺一个都不办，不当零")
    void decide_missingQty_rejectedNotZero() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", null, 10, "20GP"),
                row(null, "BL2", 10, null, "20GP"),
                row(null, "BL3", 10, 10, "20GP")), OP);

        assertEquals(3, rc.getReceivedRows(), "缺件数也是进门行，只是去向是退回");
        assertEquals(1, rc.getSignedRows());
        assertEquals(2, rc.getRejectedRows());
        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(2, rs.get(0).getStatus());
        assertTrue(rs.get(0).getRejectReason().contains("申报件数没写"));
        assertEquals(2, rs.get(1).getStatus());
        assertTrue(rs.get(1).getRejectReason().contains("实签件数没写"));
        assertNull(rs.get(0).getDeclareQty(), "缺就是缺，不许补成 0");
        assertNull(rs.get(1).getSignedQty());
        // 每一行都问得出、答得上
        assertTrue(rs.get(0).getRejectReason().contains("不当零"));
    }

    @Test
    @DisplayName("两数不相等就退，退回原因点名差在哪一条：少 2 件 / 多 2 件")
    void decide_qtyMismatch_namesDiff() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 8, "20GP"),
                row(null, "BL2", 8, 10, "20GP")), OP);

        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(2, rs.get(0).getStatus());
        assertEquals("少 2 件（实签比申报少）", rs.get(0).getRejectReason());
        assertEquals(2, rs.get(1).getStatus());
        assertEquals("多 2 件（实签比申报多）", rs.get(1).getRejectReason());
        assertEquals(0, rc.getSignedRows());
        assertEquals(2, rc.getRejectedRows());
    }

    // ---------------- 行次册上排、退行补报认原号、续办只补没办的 ----------------

    @Test
    @DisplayName("行次由册上在本串内排 1..N，交单方填的一律不认")
    void rowNo_assignedByBook() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(99, "BL1", 10, 10, "20GP"),
                row(-7, "BL2", 20, 20, "40GP")), OP);
        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(Integer.valueOf(1), rs.get(0).getRowNo());
        assertEquals(Integer.valueOf(2), rs.get(1).getRowNo());
    }

    @Test
    @DisplayName("退了隔天再报：认的还是当初那个号，补齐了就地转签；已签的不受惊动")
    void readmit_rejectedRow_keepsRowNoAndCanTurnSigned() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", 10, 8, "20GP"),    // 少 2 件，退回
                row(null, "BL2", 20, 20, "20GP")), OP); // 已签
        String bookNo = rc.getBookNo();

        THeriCusRow before = cusBookService.findRow(bookNo, 1);
        assertEquals(2, before.getStatus());

        // 隔天：BL1 补齐重报（行次交单方没地儿填），BL2 又被船代/平台照原样递了一回
        CusBookReceipt again = cusBookService.readmit(bookNo, rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 20, 20, "20GP")), OP);

        List<THeriCusRow> rs = cusBookService.listRows(bookNo);
        assertEquals(2, rs.size(), "不另起一处重复记录");
        assertEquals(Integer.valueOf(1), rs.get(0).getRowNo(), "认的还是当初那个号");
        assertEquals(1, rs.get(0).getStatus(), "补齐后就地转签");
        assertNull(rs.get(0).getRejectReason());
        assertEquals(Integer.valueOf(2), rs.get(1).getRowNo());
        assertEquals(1, rs.get(1).getStatus(), "头一遍签下的不回待签、不受惊动");
        assertEquals(2, again.getSignedRows());
        assertEquals(0, again.getRejectedRows());
    }

    @Test
    @DisplayName("同一串中途停了：续办只补没办的那几行，行次接着排")
    void readmit_resume_appendsOnlyMissing() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", 10, 10, "20GP")), OP);
        assertEquals(0, cusBookService.selectByBookNo(rc.getBookNo()).getStatus(), "在办");

        CusBookReceipt again = cusBookService.readmit(rc.getBookNo(), rows(
                row(null, "BL2", 20, 20, "40GP"),
                row(null, "BL3", 30, 30, "40HQ")), OP);

        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(3, rs.size());
        assertEquals("BL1", rs.get(0).getBlNo());
        assertEquals(1, rs.get(0).getStatus(), "办好的照旧");
        assertEquals(Integer.valueOf(2), rs.get(1).getRowNo());
        assertEquals(Integer.valueOf(3), rs.get(2).getRowNo());
        assertEquals(3, again.getReceivedRows());
        assertEquals(1, cusBookService.selectByBookNo(rc.getBookNo()).getStatus(), "补齐办结");
    }

    @Test
    @DisplayName("续办时箱型仍在门口先核一趟：拦回的不碰册上旧账，也不占新行次")
    void readmit_badKindStillBlocked() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL0", 10, 10, "77ZZ")), OP); // 头一趟门口就拦一笔
        assertEquals(1, rc.getBlockedRows());

        CusBookReceipt again = cusBookService.readmit(rc.getBookNo(), rows(
                row(null, "BL2", 20, 20, "77ZZ"),
                row(null, "BL3", 30, 30, "40GP")), OP);

        assertEquals(2, again.getReceivedRows());
        assertEquals(1, again.getBlocked().size(), "本趟门口拦回一笔");
        assertEquals(2, again.getBlockedRows(), "串头累计拦回两笔");
        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(2, rs.size());
        assertEquals("BL3", rs.get(1).getBlNo(), "拦回不占行次，BL3 接着排第 2 行");
        assertEquals(Integer.valueOf(2), rs.get(1).getRowNo());
        assertEquals(2, cusBookService.listBlocked(rc.getBookNo()).size(), "两趟门口明细都留着");
    }

    @Test
    @DisplayName("续办超出串头行数：多出的另开一串，不就地接")
    void readmit_overflow_opensAnotherBook() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 20, 20, "20GP")), OP);

        CusBookReceipt again = cusBookService.readmit(rc.getBookNo(),
                rows(row(null, "BL3", 30, 30, "40HQ")), OP);
        assertNotNull(again.getOverflowBookNo());
        assertEquals(2, cusBookService.listRows(rc.getBookNo()).size());
        List<THeriCusRow> over = cusBookService.listRows(again.getOverflowBookNo());
        assertEquals(1, over.size());
        assertEquals("BL3", over.get(0).getBlNo());
    }

    // ---------------- 重复进门：第二遍只照面、不翻账 ----------------

    @Test
    @DisplayName("同批录两回是常态：第二回只照面，不另落、不翻第一回的账")
    void admit_duplicateInSameBatch_isFaceOnly() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 8, "20GP"),   // 第一回：退回
                row(null, "BL1", 10, 8, "20GP")), OP); // 第二回：同一张单子

        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(1, rs.size(), "不许另起一处重复记录");
        assertEquals(Integer.valueOf(1), rs.get(0).getRowNo());
        assertEquals(2, rs.get(0).getStatus());
        assertEquals(1, rc.getReceivedRows());
        assertEquals(1, rc.getRejectedRows());
    }

    @Test
    @DisplayName("隔天第二遍整片再来：头一遍签下的不回待签，账一笔不动")
    void readmit_wholeSheetAgain_faceOnly() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 20, 20, "40GP"),
                row(null, "BL3", 30, 30, "40HQ")), OP);
        String bookNo = rc.getBookNo();

        // 第二遍整片丢进来（张冠李戴的件数也不许翻动旧账——认的是已签下的状态）
        CusBookReceipt again = cusBookService.readmit(bookNo, rows(
                row(null, "BL1", 99, 1, "20GP"),
                row(null, "BL2", 99, 1, "40GP"),
                row(null, "BL3", 99, 1, "40HQ")), OP);

        assertEquals(3, again.getReceivedRows());
        assertEquals(3, again.getSignedRows(), "已签的不回待签");
        assertEquals(0, again.getRejectedRows());
        for (THeriCusRow r : cusBookService.listRows(bookNo)) {
            assertEquals(1, r.getStatus());
            // 不翻账：第二遍的数不覆盖
            assertEquals(r.getBlNo().equals("BL1") ? 10 : r.getBlNo().equals("BL2") ? 20 : 30,
                    r.getSignedQty().intValue());
        }
    }

    @Test
    @DisplayName("退单只影响它自己那几行：之前签下的不动，行不飘别的串")
    void reject_onlyTouchesOwnRows() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),   // 签
                row(null, "BL2", 10, 7, "20GP")), OP); // 少 3 件，退
        String bookNo = rc.getBookNo();
        String otherNo = cusBookService.admit("1", rows(row(null, "BL9", 1, 1, "20GP")), OP)
                .getBookNo();

        cusBookService.readmit(bookNo, rows(
                row(null, "BL1", 10, 10, "20GP"),   // 已签的照面
                row(null, "BL2", 10, 7, "20GP")), OP); // 仍不对，只退它自己

        assertEquals(1, cusBookService.findRow(bookNo, 1).getStatus(), "签下的不动");
        assertEquals(2, cusBookService.findRow(bookNo, 2).getStatus(), "退的只它自己");
        assertEquals(1, cusBookService.listRows(otherNo).size(), "别的串一根毫毛都不少");
        assertNull(cusBookService.findRow(otherNo, 2), "行不会飘到别的串去");
    }

    @Test
    @DisplayName("串号不在册上不能续办（不暗地新开）")
    void readmit_unknownNo_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> cusBookService.readmit("C-NOPE", rows(row(null, "BL1", 1, 1, "20GP")), OP));
    }

    @Test
    @DisplayName("同一串并发续办同一新行：只有一行落册，头行不重、行不重")
    void concurrentReadmit_sameNewRow_noDuplicate() throws Exception {
        final String bookNo = cusBookService.admit("2",
                rows(row(null, "BL1", 10, 10, "20GP")), OP).getBookNo();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(8);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    cusBookService.readmit(bookNo, rows(row(null, "BL2", 20, 20, "20GP")), OP);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    allDone.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(allDone.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(errors.isEmpty(), "并发同串不应抛唯一约束异常: " + errors);
        assertEquals(2, cusBookService.listRows(bookNo).size());
        assertEquals(Integer.valueOf(1), cusRowMapper.selectCount(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("bl_no", "BL2")));
    }

    // ---------------- 成与不成：不抹整串；空串是正经输入 ----------------

    @Test
    @DisplayName("一行不成不抹整串：已签的照旧在册，退的走它自己的路（存量行一条不减）")
    void partialReject_keepsRestAndNeverDeletes() {
        CusBookReceipt rc = cusBookService.admit("3", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 10, 9, "20GP"),
                row(null, "BL3", 30, 30, "40HQ")), OP);

        assertEquals(2, rc.getSignedRows());
        assertEquals(1, rc.getRejectedRows());
        List<THeriCusRow> rs = cusBookService.listRows(rc.getBookNo());
        assertEquals(3, rs.size(), "退回行也在册上，一条不减");
        for (THeriCusRow r : rs) {
            assertEquals(0, r.getDelFlag(), "永不删行");
        }
        assertEquals("少 1 件（实签比申报少）", rs.get(1).getRejectReason());
    }

    @Test
    @DisplayName("空串是正经输入：回执只写空册，收讫不出，三笔全 0")
    void admit_emptyString_emptyBook() {
        CusBookReceipt rc = cusBookService.admit(null, new ArrayList<THeriCusRow>(), OP);

        assertTrue(rc.isEmptyBook());
        assertEquals(0, rc.getReceivedRows());
        assertEquals(0, rc.getSignedRows());
        assertEquals(0, rc.getRejectedRows());
        assertEquals("空册", rc.emptyText());
        assertNull(rc.ackText(), "空串不出收讫那句");
        THeriCusBook head = cusBookService.selectByBookNo(rc.getBookNo());
        assertEquals(1, head.getEmptyBook().intValue());
        assertEquals(1, head.getStatus(), "空册进门即办结");
        assertEquals(150, head.getDeclaredRows());
        assertEquals(0, cusBookService.listRows(rc.getBookNo()).size());
    }

    @Test
    @DisplayName("空册之后再递行也只照面：不许从别处拉一行补数，也不许补进空串")
    void readmit_emptyBook_staysEmpty() {
        CusBookReceipt rc = cusBookService.admit("2", new ArrayList<THeriCusRow>(), OP);
        CusBookReceipt again = cusBookService.readmit(rc.getBookNo(),
                rows(row(null, "BL1", 10, 10, "20GP")), OP);

        assertTrue(again.isEmptyBook());
        assertEquals(0, again.getReceivedRows());
        assertEquals(0, cusBookService.listRows(rc.getBookNo()).size());
    }

    @Test
    @DisplayName("非空串回执有收讫语，三笔写在里头")
    void receipt_ackText() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 10, 9, "20GP")), OP);
        assertNotNull(rc.ackText());
        assertTrue(rc.ackText().contains("进门 2"));
        assertTrue(rc.ackText().contains("签下 1"));
        assertTrue(rc.ackText().contains("退回 1"));
    }

    // ---------------- 回执三笔 + 点行倒查 + 对账点名 ----------------

    @Test
    @DisplayName("三笔恒等：签下+退回=进门，回执与串头同源（逐行定去向那一趟）")
    void receipt_threeCounts_alwaysBalance() {
        CusBookReceipt rc = cusBookService.admit("5", rows(
                row(null, "BL1", 10, 10, "20GP"),   // 签
                row(null, "BL2", 10, 8, "20GP"),    // 退
                row(null, "BL3", null, 5, "20GP"),  // 退（缺申报）
                row(null, "BL4", 10, 10, "77ZZ")), OP); // 门口拦，不进三笔
        assertEquals(rc.getSignedRows() + rc.getRejectedRows(), rc.getReceivedRows());

        THeriCusBook head = cusBookService.selectByBookNo(rc.getBookNo());
        // 页面抢先亮出的数字不算依据——以串头（那一趟返回）为准：两处必须同值
        assertEquals(head.getReceivedRows().intValue(), rc.getReceivedRows());
        assertEquals(head.getSignedRows().intValue(), rc.getSignedRows());
        assertEquals(head.getRejectedRows().intValue(), rc.getRejectedRows());

        // 续办后仍恒等
        CusBookReceipt again = cusBookService.readmit(rc.getBookNo(), rows(
                row(null, "BL5", 50, 50, "45HQ")), OP);
        assertEquals(again.getSignedRows() + again.getRejectedRows(), again.getReceivedRows());
        assertEquals(cusBookService.selectByBookNo(rc.getBookNo()).getReceivedRows().intValue(),
                again.getReceivedRows());
    }

    @Test
    @DisplayName("随便点一行倒着查：落在哪一档、差在哪一条都答得上")
    void findRow_traceBack() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 10, 6, "20GP")), OP);
        assertEquals(1, cusBookService.findRow(rc.getBookNo(), 1).getStatus());
        THeriCusRow bad = cusBookService.findRow(rc.getBookNo(), 2);
        assertEquals(2, bad.getStatus());
        assertTrue(bad.getRejectReason().contains("少 4 件"));
        assertNull(cusBookService.findRow(rc.getBookNo(), 99), "查无返回空，不许含糊");
    }

    @Test
    @DisplayName("对账：齐的时候不响；有人把数字弄花了，按行点名说清是哪一行")
    void reconcile_cleanAndNamesMismatch() {
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 10, 6, "20GP")), OP);
        assertTrue(cusBookService.reconcile(rc.getBookNo()).isEmpty(), "账齐：不响");

        // 模拟页面抢先写花：把第 1 行从已签偷偷挂回待签（正是去年那档事的形状）
        THeriCusRow first = cusBookService.findRow(rc.getBookNo(), 1);
        first.setStatus(0);
        cusRowMapper.updateById(first);

        List<String> problems = cusBookService.reconcile(rc.getBookNo());
        assertFalse(problems.isEmpty(), "两头对不齐必须查得出来");
        boolean named = false;
        for (String p : problems) {
            if (p.contains("第1行") && p.contains("BL1")) {
                named = true;
            }
        }
        assertTrue(named, "按行点名说清是哪一行，不许含糊: " + problems);

        // 退回行原因被抹也要点名（显式置 NULL：updateById 默认跳过 null）
        THeriCusRow second = cusBookService.findRow(rc.getBookNo(), 2);
        cusRowMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<THeriCusRow>()
                .eq("id", second.getId()).set("reject_reason", null));
        assertTrue(cusBookService.reconcile(rc.getBookNo()).stream()
                .anyMatch(p -> p.contains("第2行") && p.contains("差在哪一条")));
    }

    // ---------------- 老路不动：入参与返回冻结（旁路冒烟） ----------------

    @Test
    @DisplayName("老批量路/航次作业路的入参与返回冻结：新立并列路不改老路一寸契约")
    void oldPath_contractFrozen() throws Exception {
        // 老批量路 ITHeriBlRowService：入参 (String,List) 返回 int；listErrors 返回 List
        assertEquals(int.class, com.fc.v2.service.ITHeriBlRowService.class
                .getMethod("submitBatch", String.class, List.class).getReturnType());
        assertEquals(List.class, com.fc.v2.service.ITHeriBlRowService.class
                .getMethod("listErrors", String.class).getReturnType());

        // 航次作业路 ITHeriJobService：开号/核签/退回三式签名照旧
        assertEquals(String.class, com.fc.v2.service.ITHeriJobService.class
                .getMethod("openJob", String.class, List.class, String.class).getReturnType());
        assertEquals(com.fc.v2.model.auto.THeriJob.class, com.fc.v2.service.ITHeriJobService.class
                .getMethod("effectJob", String.class, java.util.Date.class, String.class)
                .getReturnType());
        assertEquals(int.class, com.fc.v2.service.ITHeriJobService.class
                .getMethod("rollbackJob", String.class, String.class).getReturnType());

        // 本路不碰老表老服务：新串进出后，本路只认串号（C 打头），与老作业号（V 打头）不相干
        CusBookReceipt rc = cusBookService.admit("2", rows(
                row(null, "BL1", 10, 10, "20GP"),
                row(null, "BL2", 10, 6, "20GP")), OP);
        assertTrue(rc.getBookNo().startsWith("C"));
        assertNull(cusBookService.selectByBookNo("V-whatever"), "老航次号在本路查不到串头，各走各的");
    }

    // ---------------- helpers ----------------

    private static THeriCusRow row(Integer rowNo, String blNo, Integer declareQty,
                                   Integer signedQty, String deviceKind) {
        THeriCusRow r = new THeriCusRow();
        r.setRowNo(rowNo); // 交单方填的行次——册上不认，仅为测试故意塞脏值
        r.setBlNo(blNo);
        r.setDeclareQty(declareQty);
        r.setSignedQty(signedQty);
        r.setDeviceKind(deviceKind);
        return r;
    }

    private static List<THeriCusRow> rows(THeriCusRow... rs) {
        List<THeriCusRow> list = new ArrayList<THeriCusRow>();
        for (THeriCusRow r : rs) {
            list.add(r);
        }
        return list;
    }
}
