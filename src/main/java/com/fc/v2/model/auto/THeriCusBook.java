package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 报关串头对象 t_heri_cus_book（整串进门路：一串一串地办）
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_cus_book")
@ApiModel(value = "THeriCusBook", description = "报关串头")
public class THeriCusBook implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 串号(册上发) */
    @TableField("book_no")
    @ApiModelProperty(value = "串号")
    private String bookNo;

    /** 串头写着的行数(空着按150；隔年仍可查当初报的行数) */
    @TableField("declared_rows")
    @ApiModelProperty(value = "串头报的行数")
    private Integer declaredRows;

    /** 进门笔数(逐行定去向那一趟计：签下+退回) */
    @TableField("received_rows")
    @ApiModelProperty(value = "进门笔数")
    private Integer receivedRows;

    /** 签下笔数 */
    @TableField("signed_rows")
    @ApiModelProperty(value = "签下笔数")
    private Integer signedRows;

    /** 退回笔数 */
    @TableField("rejected_rows")
    @ApiModelProperty(value = "退回笔数")
    private Integer rejectedRows;

    /** 门口拦回笔数(箱型不合，不落册，不进三笔) */
    @TableField("blocked_rows")
    @ApiModelProperty(value = "门口拦回笔数")
    private Integer blockedRows;

    /** 本串已排到的行次(续办接着发) */
    @TableField("last_row_no")
    @ApiModelProperty(value = "已排到的行次")
    private Integer lastRowNo;

    /** 串状态 0在办 1办结 */
    @TableField("status")
    @ApiModelProperty(value = "串状态 0在办 1办结")
    private Integer status;

    /** 是否空册 0否 1是 */
    @TableField("empty_book")
    @ApiModelProperty(value = "是否空册 0否 1是")
    private Integer emptyBook;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public Integer getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(Integer declaredRows) {
        this.declaredRows = declaredRows;
    }

    public Integer getReceivedRows() {
        return receivedRows;
    }

    public void setReceivedRows(Integer receivedRows) {
        this.receivedRows = receivedRows;
    }

    public Integer getSignedRows() {
        return signedRows;
    }

    public void setSignedRows(Integer signedRows) {
        this.signedRows = signedRows;
    }

    public Integer getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(Integer rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public Integer getBlockedRows() {
        return blockedRows;
    }

    public void setBlockedRows(Integer blockedRows) {
        this.blockedRows = blockedRows;
    }

    public Integer getLastRowNo() {
        return lastRowNo;
    }

    public void setLastRowNo(Integer lastRowNo) {
        this.lastRowNo = lastRowNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getEmptyBook() {
        return emptyBook;
    }

    public void setEmptyBook(Integer emptyBook) {
        this.emptyBook = emptyBook;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
