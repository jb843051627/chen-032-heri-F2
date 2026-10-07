package com.fc.v2.controller.admin;

import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.CusBookReceipt;
import com.fc.v2.service.ITHeriCusBookService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 报关行导表「整串进门」Controller（cus-string 形状：只收整串，逐行定去向）。
 *
 * <p>与老批量路 /heriBlRow、航次作业路各自独立：本路一串一串地办，
 * 第二遍只照面不翻账，三笔以「逐行定去向」那一趟返回为准。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "报关串册进门")
@Controller
@RequestMapping("/heriCusBook")
public class HeriCusBookController extends BaseController {

    @Autowired
    private ITHeriCusBookService heriCusBookService;

    @Log(title = "报关串册整串进门", action = "admit")
    @ApiOperation(value = "整串进门（头一遍，册上发串号）", notes = "串头行数空着按150；多出部分另开一串")
    @PostMapping("/admit")
    @RequiresPermissions("heriCusBook:admit")
    @ResponseBody
    public AjaxResult admit(@RequestParam(value = "declaredRows", required = false) String declaredRows,
                            @RequestBody(required = false) List<THeriCusRow> rows) {
        CusBookReceipt rc = heriCusBookService.admit(declaredRows, rows, operator());
        return AjaxResult.successData(200, rc);
    }

    @Log(title = "报关串册续办/重交", action = "readmit")
    @ApiOperation(value = "同串再进门", notes = "已签照面不翻账；退行认原号重核；只补没办的行")
    @PostMapping("/readmit")
    @RequiresPermissions("heriCusBook:readmit")
    @ResponseBody
    public AjaxResult readmit(@RequestParam("bookNo") String bookNo,
                              @RequestBody(required = false) List<THeriCusRow> rows) {
        CusBookReceipt rc = heriCusBookService.readmit(bookNo, rows, operator());
        return AjaxResult.successData(200, rc);
    }

    @ApiOperation(value = "回查串头（当初报的行数、三笔、拦回笔数）")
    @GetMapping("/head")
    @RequiresPermissions("heriCusBook:view")
    @ResponseBody
    public AjaxResult head(@RequestParam("bookNo") String bookNo) {
        return AjaxResult.successData(200, heriCusBookService.selectByBookNo(bookNo));
    }

    @ApiOperation(value = "回查册上行（行次升序，存量行一条不减）")
    @GetMapping("/rows")
    @RequiresPermissions("heriCusBook:view")
    @ResponseBody
    public AjaxResult rows(@RequestParam("bookNo") String bookNo) {
        return AjaxResult.successData(200, heriCusBookService.listRows(bookNo));
    }

    @ApiOperation(value = "回查门口拦回明细（不落册的那些行）")
    @GetMapping("/blocked")
    @RequiresPermissions("heriCusBook:view")
    @ResponseBody
    public AjaxResult blocked(@RequestParam("bookNo") String bookNo) {
        return AjaxResult.successData(200, heriCusBookService.listBlocked(bookNo));
    }

    @ApiOperation(value = "点行倒查：这一行落在哪一档、差在哪一条")
    @GetMapping("/row")
    @RequiresPermissions("heriCusBook:view")
    @ResponseBody
    public AjaxResult row(@RequestParam("bookNo") String bookNo,
                          @RequestParam("rowNo") int rowNo) {
        return AjaxResult.successData(200, heriCusBookService.findRow(bookNo, rowNo));
    }

    @ApiOperation(value = "对账：逐行与三笔核对，对不上按行点名")
    @GetMapping("/reconcile")
    @RequiresPermissions("heriCusBook:view")
    @ResponseBody
    public AjaxResult reconcile(@RequestParam("bookNo") String bookNo) {
        return AjaxResult.successData(200, heriCusBookService.reconcile(bookNo));
    }

    private String operator() {
        TSysUser user = ShiroUtils.getUser();
        return user == null ? null : user.getUsername();
    }
}
