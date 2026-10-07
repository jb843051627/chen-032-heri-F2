package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 整串进门门口拦回明细对象 t_heri_cus_block
 * （箱型不合的行拦在门外：不落册、不进三笔，原样留一笔在门口）
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_heri_cus_block")
@ApiModel(value = "THeriCusBlock", description = "整串进门门口拦回明细")
public class THeriCusBlock implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 串号(拦回也记在来的那串头上) */
    @TableField("book_no")
    @ApiModelProperty(value = "串号")
    private String bookNo;

    /** 门口拦回序号(仅门内计数，不占册上行次) */
    @TableField("block_no")
    @ApiModelProperty(value = "门口拦回序号")
    private Integer blockNo;

    /** 提单号(原样) */
    @TableField("bl_no")
    @ApiModelProperty(value = "提单号")
    private String blNo;

    /** 申报件数(原样) */
    @TableField("declare_qty")
    @ApiModelProperty(value = "申报件数")
    private Integer declareQty;

    /** 实签件数(原样) */
    @TableField("signed_qty")
    @ApiModelProperty(value = "实签件数")
    private Integer signedQty;

    /** 箱型(原样，含空) */
    @TableField("device_kind")
    @ApiModelProperty(value = "箱型")
    private String deviceKind;

    /** 拦回原因(箱型不合(xx)/箱型没写) */
    @TableField("reason")
    @ApiModelProperty(value = "拦回原因")
    private String reason;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField("create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

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

    public Integer getBlockNo() {
        return blockNo;
    }

    public void setBlockNo(Integer blockNo) {
        this.blockNo = blockNo;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
}
