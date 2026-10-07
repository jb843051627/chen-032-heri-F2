package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

/**
 * 整串进门回执：末尾三笔（进门／签下／退回），后两笔加起来等于头一笔，
 * 三个数都取自「逐行定去向」那一趟，另带门口拦回笔数与溢出另开的新串号。
 *
 * <p>空串是正经输入：{@link #emptyBook}=true 时三笔全为 0、无收讫语，只写「空册」。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
public class CusBookReceipt {

    /** 本趟落的那一串（溢出时是第一串） */
    private String bookNo;

    /** 串头当初报的行数（空着按 150） */
    private int declaredRows;

    /** 门口拦回笔数（箱型不合，不落册，不进三笔） */
    private int blockedRows;

    /** 进门笔数 = 签下 + 退回（逐行定去向那一趟计） */
    private int receivedRows;

    /** 签下笔数 */
    private int signedRows;

    /** 退回笔数 */
    private int rejectedRows;

    /** 空册：三笔全 0，无收讫 */
    private boolean emptyBook;

    /** 串头数字之外多出来的部分另开的一串（没有则 null；绝不就地往下接） */
    private String overflowBookNo;

    /** 溢出那串自己的回执（没有则 null） */
    private CusBookReceipt overflow;

    /** 门口逐行拦回明细（原样，不落册） */
    private List<BlockedLine> blocked = new ArrayList<BlockedLine>();

    /** 册上退回行逐行原因（哪一行、差在哪一条，如「少 2 件」） */
    private List<RejectedLine> rejected = new ArrayList<RejectedLine>();

    /** 收讫语（空串不出） */
    public String ackText() {
        if (this.emptyBook) {
            return null;
        }
        return "收讫：进门 " + this.receivedRows + "，签下 " + this.signedRows
                + "，退回 " + this.rejectedRows;
    }

    /** 空册语（只有空串写这一笔） */
    public String emptyText() {
        return this.emptyBook ? "空册" : null;
    }

    public static class BlockedLine {
        /** 门口拦回序号（门内计数，不占册上行次） */
        private final int blockNo;
        private final String blNo;
        private final String deviceKind;
        private final String reason;

        public BlockedLine(int blockNo, String blNo, String deviceKind, String reason) {
            this.blockNo = blockNo;
            this.blNo = blNo;
            this.deviceKind = deviceKind;
            this.reason = reason;
        }

        public int getBlockNo() {
            return blockNo;
        }

        public String getBlNo() {
            return blNo;
        }

        public String getDeviceKind() {
            return deviceKind;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class RejectedLine {
        private final int rowNo;
        private final String blNo;
        private final String reason;

        public RejectedLine(int rowNo, String blNo, String reason) {
            this.rowNo = rowNo;
            this.blNo = blNo;
            this.reason = reason;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getBlNo() {
            return blNo;
        }

        public String getReason() {
            return reason;
        }
    }

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public int getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(int declaredRows) {
        this.declaredRows = declaredRows;
    }

    public int getBlockedRows() {
        return blockedRows;
    }

    public void setBlockedRows(int blockedRows) {
        this.blockedRows = blockedRows;
    }

    public int getReceivedRows() {
        return receivedRows;
    }

    public void setReceivedRows(int receivedRows) {
        this.receivedRows = receivedRows;
    }

    public int getSignedRows() {
        return signedRows;
    }

    public void setSignedRows(int signedRows) {
        this.signedRows = signedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(int rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public boolean isEmptyBook() {
        return emptyBook;
    }

    public void setEmptyBook(boolean emptyBook) {
        this.emptyBook = emptyBook;
    }

    public String getOverflowBookNo() {
        return overflowBookNo;
    }

    public void setOverflowBookNo(String overflowBookNo) {
        this.overflowBookNo = overflowBookNo;
    }

    public CusBookReceipt getOverflow() {
        return overflow;
    }

    public void setOverflow(CusBookReceipt overflow) {
        this.overflow = overflow;
    }

    public List<BlockedLine> getBlocked() {
        return blocked;
    }

    public void setBlocked(List<BlockedLine> blocked) {
        this.blocked = blocked;
    }

    public List<RejectedLine> getRejected() {
        return rejected;
    }

    public void setRejected(List<RejectedLine> rejected) {
        this.rejected = rejected;
    }
}
