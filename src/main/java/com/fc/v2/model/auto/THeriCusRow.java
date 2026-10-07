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
 * 报关册上行对象 t_heri_cus_row（整串进门路：行去向 0待签 1已签 2退回）
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_cus_row")
@ApiModel(value = "THeriCusRow", description = "报关册上行")
public class THeriCusRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 串号 */
    @TableField("book_no")
    @ApiModelProperty(value = "串号")
    private String bookNo;

    /** 行次(册上在本串内排1..N，无手输；退行补报认原号) */
    @TableField("row_no")
    @ApiModelProperty(value = "行次")
    private Integer rowNo;

    /** 提单号 */
    @TableField("bl_no")
    @ApiModelProperty(value = "提单号")
    private String blNo;

    /** 申报件数(空=没写，缺即不办，不当零) */
    @TableField("declare_qty")
    @ApiModelProperty(value = "申报件数")
    private Integer declareQty;

    /** 实签件数(空=没写，缺即不办，不当零) */
    @TableField("signed_qty")
    @ApiModelProperty(value = "实签件数")
    private Integer signedQty;

    /** 箱型(门口已核过) */
    @TableField("device_kind")
    @ApiModelProperty(value = "箱型")
    private String deviceKind;

    /** 行去向 0待签 1已签 2退回 */
    @TableField("status")
    @ApiModelProperty(value = "行去向 0待签 1已签 2退回")
    private Integer status;

    /** 退回原因(如 少2件/多1件/申报件数没写) */
    @TableField("reject_reason")
    @ApiModelProperty(value = "退回原因")
    private String rejectReason;

    /** 删除标记 0正常 1删除(存量行一条不减) */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
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

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getBlNo() {
        return blNo;
    }

    public void setBlNo(String blNo) {
        this.blNo = blNo;
    }

    public Integer getDeclareQty() {
        return declareQty;
    }

    public void setDeclareQty(Integer declareQty) {
        this.declareQty = declareQty;
    }

    public Integer getSignedQty() {
        return signedQty;
    }

    public void setSignedQty(Integer signedQty) {
        this.signedQty = signedQty;
    }

    public String getDeviceKind() {
        return deviceKind;
    }

    public void setDeviceKind(String deviceKind) {
        this.deviceKind = deviceKind;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
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
