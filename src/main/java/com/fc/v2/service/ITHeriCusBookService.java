package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.THeriCusBlock;
import com.fc.v2.model.auto.THeriCusBook;
import com.fc.v2.model.auto.THeriCusRow;
import com.fc.v2.model.custom.CusBookReceipt;

/**
 * 报关行导表「整串进门」Service（cus-string 形状：一串一串地办，逐行定去向）。
 *
 * <p>本路是 2026-10 新立的并列路，与老批量路 {@code ITHeriBlRowService}、
 * 航次作业路 {@code ITHeriJobService} 各走各的：老路入参与返回冻结不改，
 * 往年存量行一条不减、照旧数得着。将来按船期/按船代分串，再立并列路，本路不动。</p>
 *
 * <h3>规矩（写死，不许改口径）</h3>
 * <ol>
 *   <li><b>只收整串。</b>串头那格写着几行就只当几行；那格空着按 150 行算。
 *       串头数字之外再多的部分另开一串，不许就地往下接。原串记下的行数就是当初报的行数，
 *       隔年还查得回来（{@code declared_rows} 落串头，永不改）。</li>
 *   <li><b>解析只跑一趟，先于落册。</b>串头写的箱型对着校验档逐行核：箱型不合的行拦在门外、
 *       不落册（原样落门口拦回明细）；箱型那格空着按「没写」拦，不当「都可以」。
 *       不合规的当场退，不许先落库再回头撤。</li>
 *   <li><b>两个件数都得有。</b>申报件数与册上实签件数，缺哪一个都不办，也不把缺的当零；
 *       两数不相等就退，退回原因点名差在哪一条（「少 2 件」／「多 1 件」），
 *       每一行都问得出、答得上。</li>
 *   <li><b>行次由册上在本串内自己排（1..N），没有手输的地方。</b>一行退了隔天再报，
 *       认的还是当初那个号（按提单号认出原退行，号不变）。同一串中途停了，续办只补没办的
 *       那几行，办好的不受惊动。</li>
 *   <li><b>第二遍只照面、不翻账。</b>头一遍签下的行不回待签，也不许另起一处重复记录
 *       （同批内、跨批重样都只照面）。退单只影响它自己那几行，之前签下的不动，
 *       行也不会飘到别的串去。</li>
 *   <li><b>一行不成不抹整串。</b>已签的照旧在册，退掉的走它自己的路。
 *       空串是正经输入：回执只写「空册」，收讫那句不出，也不许从别串拉行补数。</li>
 *   <li><b>回执末尾三笔</b>（进门／签下／退回）：签下+退回=进门。三个数都取自
 *       「逐行定去向」那一趟，不必另跑一趟核；页面抢先亮出来的数字不算依据，
 *       两头对不齐以那一趟返回为准。门口拦回笔数单列，不进三笔。</li>
 *   <li><b>点行倒查 + 对账点名。</b>随便点一行倒着查，看它落在哪一档，跟三笔加出来的数
 *       对不对得上；对不上按行点名说清是哪一行，不许含糊。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITHeriCusBookService {

    /** 串头行数格空着时的认账行数 */
    int DEFAULT_DECLARED_ROWS = 150;

    /**
     * 整串进门（头一遍）：册上发串号，一串一串地办。
     *
     * <p>解析一趟跑完才落册：箱型不合/没写的拦在门外（不落册），其余进册逐行定去向。
     * 实际行数多于串头数字的，多出部分另开一串（回执 {@code overflow} 链），绝不就地接。
     * 空串（rows 为空）是正经输入：串头照落（空册标记=1），三笔全 0，只回「空册」，
     * 不出收讫语，不从别串拉行。</p>
     *
     * @param declaredRowsText 串头那格原文；{@code null}/空白按 150；非正整数当场拒
     * @param rows             整串明细（交单方填的行次一律不认）
     * @param operator         进门经手人（货代/船代/平台）
     * @return 回执（三笔取自本趟逐行定去向；溢出另串挂在 overflow 上）
     */
    CusBookReceipt admit(String declaredRowsText, List<THeriCusRow> rows, String operator);

    /**
     * 同串再进门（隔天补报／中途续办／同批第二遍）：认串号。
     *
     * <p>同一把锁内只动这一串：已签下的行照面不翻账、不重落；退掉的行按提单号认出原号，
     * 号不变、就地重核（补齐且相等则转签，仍不对则继续退它自己）；没见过的行续排在后，
     * 只补没办的那几行。箱型仍在门口先核一趟，拦回的不碰册上旧账。</p>
     *
     * @param bookNo   头一遍册上发的串号（册上没有则拒，不暗地新开）
     * @param rows     本趟再交的整串明细
     * @param operator 经手人
     * @return 回执（三笔为本串在册行此刻的账，由本趟一并点出，不另跑核账趟）
     */
    CusBookReceipt readmit(String bookNo, List<THeriCusRow> rows, String operator);

    /** 按串号回查串头（含当初报的行数、三笔、拦回笔数；隔年可查） */
    THeriCusBook selectByBookNo(String bookNo);

    /** 回查串内册上行（行次升序；存量行一条不减，del_flag 永不置 1） */
    List<THeriCusRow> listRows(String bookNo);

    /** 回查门口拦回明细（门内序号升序，原样留着） */
    List<THeriCusBlock> listBlocked(String bookNo);

    /** 点行倒查：这一行落在哪一档（0待签 1已签 2退回），退回带逐行原因；查无返回 null */
    THeriCusRow findRow(String bookNo, int rowNo);

    /**
     * 对账：把册上行逐行点一遍，与串头三笔核对（签下+退回=进门；逐行状态/原因齐备）。
     * 全对得上返回空表；对不上逐条按行点名（串号、行次、提单号、差在哪），不许含糊。
     */
    List<String> reconcile(String bookNo);
}
