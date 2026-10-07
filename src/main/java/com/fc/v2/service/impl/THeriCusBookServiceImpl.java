package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.THeriCusBlockMapper;
import com.fc.v2.mapper.auto.THeriCusBookMapper;
import com.fc.v2.mapper.auto.THeriCusRowMapper;
import com.fc.v2.mapper.auto.THeriDevKindRuleMapper;
import com.fc.v2.model.auto.THeriCusBlock;
import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.auto.THeriDevKindRule;
import com.fc.v2.model.custom.CusBookReceipt;
import com.fc.v2.model.custom.CusBookReceipt.BlockedLine;
import com.fc.v2.model.custom.CusBookReceipt.RejectedLine;
import com.fc.v2.service.ITHeriCusBookService;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 报关行导表「整串进门」Service实现（cus-string 形状）。
 *
 * <p>落账结构：</p>
 * <ul>
 *   <li>t_heri_cus_book：串头，一串一行。{@code declared_rows} 是串头当初报的行数（永不改，
 *       隔年可查）；三笔（received/signed/rejected）与 blocked 全部取自每趟「逐行定去向」；</li>
 *   <li>t_heri_cus_row：册上行，行次册上在本串内排 1..N，交单方填的不认；退单只动本行，
 *       del_flag 永不置 1，存量行一条不减；</li>
 *   <li>t_heri_cus_block：门口拦回明细，原样留底，不落册、不占行次、不进三笔。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class THeriCusBookServiceImpl implements ITHeriCusBookService {

    // ---- 串状态 ----
    private static final int BOOK_OPEN = 0;
    private static final int BOOK_DONE = 1;

    // ---- 行去向：0待签 1已签 2退回 ----
    private static final int ROW_PENDING = 0;
    private static final int ROW_SIGNED = 1;
    private static final int ROW_REJECTED = 2;

    /** 册上发号（串号） */
    private static final SnowflakeIdWorker ID_WORKER = new SnowflakeIdWorker(2, 2);

    /** 每一串一段锁：同串再交排队，异串各锁各的，行不会飘串 */
    private final ConcurrentHashMap<String, Object> segmentLocks = new ConcurrentHashMap<String, Object>();

    @Resource
    private THeriCusBookMapper cusBookMapper;
    @Resource
    private THeriCusRowMapper cusRowMapper;
    @Resource
    private THeriCusBlockMapper cusBlockMapper;
    @Resource
    private THeriDevKindRuleMapper devKindRuleMapper;

    // ============================ 进门 ============================

    @Override
    public CusBookReceipt admit(String declaredRowsText, List<THeriCusRow> rows, String operator) {
        final int declared = parseDeclaredRows(declaredRowsText);
        final List<THeriCusRow> in = rows == null ? new ArrayList<THeriCusRow>() : rows;

        // 空串是正经输入：串头照落（空册），三笔全 0，只回「空册」，不出收讫，不拉别串补数
        if (in.isEmpty()) {
            String bookNo = newBookNo();
            THeriCusBook head = newHead(bookNo, declared, operator);
            head.setReceivedRows(0);
            head.setSignedRows(0);
            head.setRejectedRows(0);
            head.setBlockedRows(0);
            head.setLastRowNo(0);
            head.setStatus(BOOK_DONE);
            head.setEmptyBook(1);
            this.cusBookMapper.insert(head);

            CusBookReceipt rc = new CusBookReceipt();
            rc.setBookNo(bookNo);
            rc.setDeclaredRows(declared);
            rc.setEmptyBook(true);
            return rc;
        }

        // 串头数字之外再多的部分：切出来另开一串，绝不就地往下接
        List<THeriCusRow> main = in;
        List<THeriCusRow> overflow = null;
        if (in.size() > declared) {
            main = new ArrayList<THeriCusRow>(in.subList(0, declared));
            overflow = new ArrayList<THeriCusRow>(in.subList(declared, in.size()));
        }

        String firstNo = newBookNo();
        CusBookReceipt first = registerFreshBook(firstNo, declared, main, operator, null);

        if (overflow != null) {
            // 溢出部分自成一串：它自己的串头行数就按它自己的行数记
            String overNo = newBookNo();
            CusBookReceipt over = registerFreshBook(overNo, overflow.size(), overflow, operator,
                    "由串" + firstNo + "串头行数之外溢出另开");
            first.setOverflowBookNo(overNo);
            first.setOverflow(over);
        }
        return first;
    }

    @Override
    public CusBookReceipt readmit(String bookNo, List<THeriCusRow> rows, String operator) {
        if (bookNo == null || bookNo.trim().isEmpty()) {
            throw new IllegalArgumentException("串号不能为空");
        }
        if (rows == null) {
            rows = new ArrayList<THeriCusRow>();
        }
        synchronized (lockOf(bookNo)) {
            THeriCusBook head = selectByBookNo(bookNo);
            if (head == null) {
                // 册上没有的号不暗地新开——头一遍得走 admit 拿号
                throw new IllegalArgumentException("串号不在册上，不能续办: " + bookNo);
            }
            if (Integer.valueOf(1).equals(head.getEmptyBook())) {
                // 空册续交仍按空串规矩：只照面，不补数
                return receiptFromBook(head, new ArrayList<BlockedLine>(),
                        new ArrayList<RejectedLine>(), true);
            }

            // 解析一趟：门口逐行核箱型，拦回的不碰册上旧账
            Door door = scanDoor(bookNo, rows, nextBlockSeq(bookNo), operator);

            // 已在册的行（行次不变）：按提单号认
            List<THeriCusRow> existed = listRows(bookNo);
            Map<String, THeriCusRow> byBl = new HashMap<String, THeriCusRow>();
            for (THeriCusRow r : existed) {
                if (r.getBlNo() != null) {
                    byBl.put(r.getBlNo(), r);
                }
            }
            Set<String> seenThisRun = new HashSet<String>();
            int rowSeq = head.getLastRowNo() == null ? 0 : head.getLastRowNo();
            int slotsLeft = head.getDeclaredRows()
                    - (head.getReceivedRows() == null ? 0 : head.getReceivedRows());

            List<THeriCusRow> touchedRejected = new ArrayList<THeriCusRow>();
            List<THeriCusRow> extraLines = new ArrayList<THeriCusRow>();

            for (THeriCusRow src : door.passed) {
                String bl = trim(src.getBlNo());
                if (bl != null) {
                    if (!seenThisRun.add(bl)) {
                        continue; // 同批内第二回：只照面
                    }
                    THeriCusRow old = byBl.get(bl);
                    if (old != null) {
                        if (Integer.valueOf(ROW_SIGNED).equals(old.getStatus())) {
                            // 头一遍签下的：不回待签、不覆盖、不另落，只照面
                            continue;
                        }
                        // 退了隔天再报：认的还是当初那个号（rowNo 不动），就地重核这一趟
                        decideRow(old, src, operator);
                        updateLedgerRow(old);
                        if (Integer.valueOf(ROW_REJECTED).equals(old.getStatus())) {
                            touchedRejected.add(old);
                        }
                        continue;
                    }
                }
                // 没见过的行：只补缺额；串头行数之外的多出来的部分不就地接，另开一串
                if (slotsLeft <= 0) {
                    extraLines.add(src);
                    continue;
                }
                rowSeq++;
                slotsLeft--;
                THeriCusRow r = newLedgerRow(head.getBookNo(), rowSeq, src, operator);
                decideRow(r, src, operator);
                this.cusRowMapper.insert(r);
                if (Integer.valueOf(ROW_REJECTED).equals(r.getStatus())) {
                    touchedRejected.add(r);
                }
            }

            CusBookReceipt overflowRc = null;
            String overflowNo = null;
            if (!extraLines.isEmpty()) {
                overflowNo = newBookNo();
                overflowRc = registerFreshBook(overflowNo, extraLines.size(), extraLines, operator,
                        "由串" + bookNo + "续办时串头行数之外溢出另开");
            }

            // 本趟门口拦回的原样留底（不占行次、不进三笔、不碰旧账）
            for (THeriCusBlock b : door.blocks) {
                this.cusBlockMapper.insert(b);
            }

            // 三笔由这一趟在册上逐行点出，不另跑核账趟；已签下、已退回不在本趟的也照旧在册
            head.setLastRowNo(rowSeq);
            return settleBook(head, operator, door.blocks, touchedRejected, overflowNo, overflowRc);
        }
    }

    // ============================ 落册 ============================

    /**
     * 头一遍落一本新串：门口解析一趟（不合的当场拦、不落册），进门的行逐行定去向，
     * 三笔与行次同趟产出。
     */
    private CusBookReceipt registerFreshBook(String bookNo, int declaredRows,
                                             List<THeriCusRow> lines, String operator,
                                             String remark) {
        synchronized (lockOf(bookNo)) {
            Door door = scanDoor(bookNo, lines, 0, operator);

            THeriCusBook head = newHead(bookNo, declaredRows, operator);
            if (remark != null) {
                head.setRemark(remark);
            }

            int seq = 0;
            List<THeriCusRow> rejectedRows = new ArrayList<THeriCusRow>();
            Set<String> seen = new HashSet<String>();
            for (THeriCusRow src : door.passed) {
                String bl = trim(src.getBlNo());
                if (bl != null && !seen.add(bl)) {
                    continue; // 同批录两回是常态：第二回只照面，不另起一处
                }
                seq++;
                THeriCusRow r = newLedgerRow(bookNo, seq, src, operator);
                decideRow(r, src, operator);
                this.cusRowMapper.insert(r);
                if (Integer.valueOf(ROW_REJECTED).equals(r.getStatus())) {
                    rejectedRows.add(r);
                }
            }
            head.setLastRowNo(seq);
            for (THeriCusBlock b : door.blocks) {
                this.cusBlockMapper.insert(b);
            }
            // 串头行数没到齐 => 在办，等续办只补没办的；到齐（空串另有空册口径）即办结
            head.setStatus(seq < declaredRows ? BOOK_OPEN : BOOK_DONE);
            this.cusBookMapper.insert(head);
            return settleBook(head, operator, door.blocks, rejectedRows, null, null);
        }
    }

    /** 门口那一趟：逐行对着校验档核箱型；不合/没写的拦在门外，原样留一笔，不落册。 */
    private Door scanDoor(String bookNo, List<THeriCusRow> lines, int blockSeqStart, String operator) {
        Door door = new Door();
        Map<String, Boolean> allowedCache = new HashMap<String, Boolean>();
        int blockSeq = blockSeqStart;
        Date now = new Date();
        for (THeriCusRow src : lines) {
            String kind = trim(src.getDeviceKind());
            String reason;
            if (kind == null) {
                // 箱型那格空着按「没写」核，不当「都可以」
                reason = "箱型没写，拦在门外";
            } else if (isKindAllowed(kind, allowedCache)) {
                door.passed.add(src);
                continue;
            } else {
                reason = "箱型不合(" + kind + ")，拦在门外";
            }
            blockSeq++;
            THeriCusBlock b = new THeriCusBlock();
            b.setBookNo(bookNo);
            b.setBlockNo(blockSeq);
            b.setBlNo(trim(src.getBlNo()));
            b.setDeclareQty(src.getDeclareQty());
            b.setSignedQty(src.getSignedQty());
            b.setDeviceKind(kind); // 空也原样记着
            b.setReason(reason);
            b.setDelFlag(0);
            b.setCreateBy(operator);
            b.setCreateTime(now);
            door.blocks.add(b);
        }
        return door;
    }

    /** 校验档：在册、未撤下才算合；查无/撤下一律不合。 */
    private boolean isKindAllowed(String kind, Map<String, Boolean> cache) {
        Boolean cached = cache.get(kind);
        if (cached != null) {
            return cached;
        }
        THeriDevKindRule rule = this.devKindRuleMapper.selectOne(new QueryWrapper<THeriDevKindRule>()
                .eq("device_kind", kind).eq("del_flag", 0).last("limit 1"));
        boolean ok = rule != null && Integer.valueOf(0).equals(rule.getStatus());
        cache.put(kind, ok);
        return ok;
    }

    /**
     * 逐行定去向（三笔的唯一出处）：申报件数与册上实签件数两个都得有，缺哪一个都不办、
     * 不当零；两数不等就退，原因点名差在哪一条。
     */
    private void decideRow(THeriCusRow r, THeriCusRow src, String operator) {
        r.setDeclareQty(src.getDeclareQty());
        r.setSignedQty(src.getSignedQty());
        String kind = trim(src.getDeviceKind());
        if (kind != null) {
            r.setDeviceKind(kind);
        }
        r.setUpdateBy(operator);
        r.setUpdateTime(new Date());

        if (src.getDeclareQty() == null) {
            r.setStatus(ROW_REJECTED);
            r.setRejectReason("申报件数没写，不办（不当零）");
            return;
        }
        if (src.getSignedQty() == null) {
            r.setStatus(ROW_REJECTED);
            r.setRejectReason("册上实签件数没写，不办（不当零）");
            return;
        }
        int declare = src.getDeclareQty();
        int signed = src.getSignedQty();
        if (declare == signed) {
            r.setStatus(ROW_SIGNED);
            r.setRejectReason(null);
            return;
        }
        int diff = declare - signed;
        r.setStatus(ROW_REJECTED);
        r.setRejectReason(diff > 0 ? "少 " + diff + " 件（实签比申报少）"
                                   : "多 " + (-diff) + " 件（实签比申报多）");
    }

    /** 三笔同趟点账：把在册行逐行点一遍回写串头，并组回执；签下+退回恒等于进门。 */
    private CusBookReceipt settleBook(THeriCusBook head, String operator,
                                      List<THeriCusBlock> newBlocks,
                                      List<THeriCusRow> newRejected,
                                      String overflowNo, CusBookReceipt overflow) {
        List<THeriCusRow> all = listRows(head.getBookNo());
        int received = all.size();
        int signed = 0;
        int rejected = 0;
        for (THeriCusRow r : all) {
            if (Integer.valueOf(ROW_SIGNED).equals(r.getStatus())) {
                signed++;
            } else if (Integer.valueOf(ROW_REJECTED).equals(r.getStatus())) {
                rejected++;
            }
        }
        head.setReceivedRows(received);
        head.setSignedRows(signed);
        head.setRejectedRows(rejected);
        int blocked = (head.getBlockedRows() == null ? 0 : head.getBlockedRows())
                + (newBlocks == null ? 0 : newBlocks.size());
        head.setBlockedRows(blocked);
        if (received >= head.getDeclaredRows()) {
            head.setStatus(BOOK_DONE);
        }
        head.setUpdateBy(operator);
        head.setUpdateTime(new Date());
        this.cusBookMapper.updateById(head);

        List<BlockedLine> blockedLines = new ArrayList<BlockedLine>();
        if (newBlocks != null) {
            for (THeriCusBlock b : newBlocks) {
                blockedLines.add(new BlockedLine(b.getBlockNo(), b.getBlNo(),
                        b.getDeviceKind(), b.getReason()));
            }
        }
        List<RejectedLine> rejectedLines = new ArrayList<RejectedLine>();
        if (newRejected != null) {
            for (THeriCusRow r : newRejected) {
                rejectedLines.add(new RejectedLine(r.getRowNo(), r.getBlNo(), r.getRejectReason()));
            }
        }
        CusBookReceipt rc = receiptFromBook(head, blockedLines, rejectedLines, false);
        if (overflow != null) {
            rc.setOverflowBookNo(overflowNo);
            rc.setOverflow(overflow);
        }
        return rc;
    }

    private CusBookReceipt receiptFromBook(THeriCusBook head, List<BlockedLine> blocked,
                                           List<RejectedLine> rejected, boolean empty) {
        CusBookReceipt rc = new CusBookReceipt();
        rc.setBookNo(head.getBookNo());
        rc.setDeclaredRows(head.getDeclaredRows() == null ? 0 : head.getDeclaredRows());
        rc.setBlockedRows(head.getBlockedRows() == null ? 0 : head.getBlockedRows());
        rc.setReceivedRows(head.getReceivedRows() == null ? 0 : head.getReceivedRows());
        rc.setSignedRows(head.getSignedRows() == null ? 0 : head.getSignedRows());
        rc.setRejectedRows(head.getRejectedRows() == null ? 0 : head.getRejectedRows());
        rc.setEmptyBook(empty || Integer.valueOf(1).equals(head.getEmptyBook()));
        if (blocked != null) {
            rc.getBlocked().addAll(blocked);
        }
        if (rejected != null) {
            rc.setRejected(rejected);
        }
        return rc;
    }

    // ============================ 倒查 / 对账 ============================

    @Override
    public THeriCusBook selectByBookNo(String bookNo) {
        if (bookNo == null) {
            return null;
        }
        return this.cusBookMapper.selectOne(new QueryWrapper<THeriCusBook>()
                .eq("book_no", bookNo).eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public List<THeriCusRow> listRows(String bookNo) {
        if (bookNo == null) {
            return new ArrayList<THeriCusRow>();
        }
        return this.cusRowMapper.selectList(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("del_flag", 0).orderByAsc("row_no"));
    }

    @Override
    public List<THeriCusBlock> listBlocked(String bookNo) {
        if (bookNo == null) {
            return new ArrayList<THeriCusBlock>();
        }
        return this.cusBlockMapper.selectList(new QueryWrapper<THeriCusBlock>()
                .eq("book_no", bookNo).eq("del_flag", 0).orderByAsc("block_no"));
    }

    @Override
    public THeriCusRow findRow(String bookNo, int rowNo) {
        if (bookNo == null) {
            return null;
        }
        return this.cusRowMapper.selectOne(new QueryWrapper<THeriCusRow>()
                .eq("book_no", bookNo).eq("row_no", rowNo).eq("del_flag", 0).last("limit 1"));
    }

    @Override
    public List<String> reconcile(String bookNo) {
        List<String> problems = new ArrayList<String>();
        THeriCusBook head = selectByBookNo(bookNo);
        if (head == null) {
            problems.add("串号不在册上: " + bookNo);
            return problems;
        }
        List<THeriCusRow> rows = listRows(bookNo);
        int signed = 0;
        int rejected = 0;
        Set<Integer> nos = new HashSet<Integer>();
        for (THeriCusRow r : rows) {
            if (r.getRowNo() == null || !nos.add(r.getRowNo())) {
                problems.add("串" + bookNo + " 行次缺失或重号: id=" + r.getId()
                        + "，行次=" + r.getRowNo());
            }
            Integer st = r.getStatus();
            if (Integer.valueOf(ROW_SIGNED).equals(st)) {
                signed++;
            } else if (Integer.valueOf(ROW_REJECTED).equals(st)) {
                rejected++;
                if (r.getRejectReason() == null || r.getRejectReason().trim().isEmpty()) {
                    problems.add("串" + bookNo + " 第" + r.getRowNo() + "行(提单号="
                            + r.getBlNo() + ")挂着退回却没写差在哪一条");
                }
            } else {
                // 还挂待签：三笔点不上它，按行点名
                problems.add("串" + bookNo + " 第" + r.getRowNo() + "行(提单号=" + r.getBlNo()
                        + ")还挂着待签，进门/签下/退回三笔都点不上");
            }
        }
        int received = rows.size();
        if (signed + rejected != received) {
            problems.add("串" + bookNo + " 逐行点账：签下 " + signed + " + 退回 " + rejected
                    + " ≠ 进门 " + received);
        }
        Integer headReceived = head.getReceivedRows();
        Integer headSigned = head.getSignedRows();
        Integer headRejected = head.getRejectedRows();
        if (headReceived == null || headReceived != received) {
            problems.add("串" + bookNo + " 串头进门笔数(" + headReceived
                    + ")与逐行点出的(" + received + ")对不上");
        }
        if (headSigned == null || headSigned != signed) {
            problems.add("串" + bookNo + " 串头签下笔数(" + headSigned
                    + ")与逐行点出的(" + signed + ")对不上");
        }
        if (headRejected == null || headRejected != rejected) {
            problems.add("串" + bookNo + " 串头退回笔数(" + headRejected
                    + ")与逐行点出的(" + rejected + ")对不上");
        }
        return problems;
    }

    // ============================ 零碎 ============================

    private THeriCusBook newHead(String bookNo, int declaredRows, String operator) {
        THeriCusBook head = new THeriCusBook();
        head.setBookNo(bookNo);
        head.setDeclaredRows(declaredRows);
        head.setReceivedRows(0);
        head.setSignedRows(0);
        head.setRejectedRows(0);
        head.setBlockedRows(0);
        head.setLastRowNo(0);
        head.setStatus(BOOK_OPEN);
        head.setEmptyBook(0);
        head.setDelFlag(0);
        head.setCreateBy(operator);
        head.setCreateTime(new Date());
        return head;
    }

    /**
     * 更新册上行：显式逐列写（不用 updateById——它默认跳过 null，
     * 退行补齐转签时旧的退回原因/reject_reason 必须清成 null，不能赖着）。
     */
    private void updateLedgerRow(THeriCusRow r) {
        UpdateWrapper<THeriCusRow> u = new UpdateWrapper<THeriCusRow>()
                .eq("id", r.getId())
                .set("declare_qty", r.getDeclareQty())
                .set("signed_qty", r.getSignedQty())
                .set("device_kind", r.getDeviceKind())
                .set("status", r.getStatus())
                .set("reject_reason", r.getRejectReason())
                .set("update_by", r.getUpdateBy())
                .set("update_time", r.getUpdateTime());
        this.cusRowMapper.update(null, u);
    }

    private THeriCusRow newLedgerRow(String bookNo, int rowNo, THeriCusRow src, String operator) {        THeriCusRow r = new THeriCusRow();
        r.setBookNo(bookNo);
        r.setRowNo(rowNo); // 行次册上排，交单方填的一律不认
        r.setBlNo(trim(src.getBlNo()));
        r.setDeviceKind(trim(src.getDeviceKind()));
        r.setStatus(ROW_PENDING);
        r.setDelFlag(0);
        r.setCreateBy(operator);
        r.setCreateTime(new Date());
        return r;
    }

    /** 串头那格：空着按 150；写了就得是正整数，别的写法当场拒。 */
    private int parseDeclaredRows(String text) {
        if (text == null || text.trim().isEmpty()) {
            return DEFAULT_DECLARED_ROWS;
        }
        try {
            int n = Integer.parseInt(text.trim());
            if (n <= 0) {
                throw new IllegalArgumentException("串头行数得是正整数: " + text);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("串头行数不是整数，整串不收: " + text);
        }
    }

    private int nextBlockSeq(String bookNo) {
        THeriCusBlock last = this.cusBlockMapper.selectOne(new QueryWrapper<THeriCusBlock>()
                .eq("book_no", bookNo).eq("del_flag", 0)
                .orderByDesc("block_no").last("limit 1"));
        return last == null || last.getBlockNo() == null ? 0 : last.getBlockNo();
    }

    private String newBookNo() {
        return "C" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ID_WORKER.nextId();
    }

    private Object lockOf(String bookNo) {
        Object exist = this.segmentLocks.get(bookNo);
        if (exist != null) {
            return exist;
        }
        Object fresh = new Object();
        Object old = this.segmentLocks.putIfAbsent(bookNo, fresh);
        return old == null ? fresh : old;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 门口一趟的结果：进门的与拦回的分开；解析只跑这一趟。 */
    private static class Door {
        final List<THeriCusRow> passed = new ArrayList<THeriCusRow>();
        final List<THeriCusBlock> blocks = new ArrayList<THeriCusBlock>();
    }
}
