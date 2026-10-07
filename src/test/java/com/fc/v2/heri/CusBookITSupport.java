package com.fc.v2.heri;

import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.THeriCusBlockMapper;
import com.fc.v2.mapper.auto.THeriCusBookMapper;
import com.fc.v2.mapper.auto.THeriCusRowMapper;
import com.fc.v2.mapper.auto.THeriDevKindRuleMapper;
import com.fc.v2.service.impl.THeriCusBookServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 整串进门链路测试夹具：H2 内存库(MySQL 兼容) + 真实 MyBatis-Plus mapper，不启 Spring 容器。
 *
 * @author fuce
 * @date 2026-10-07
 */
public abstract class CusBookITSupport {

    protected JdbcDataSource dataSource;
    protected SqlSessionTemplate session;

    protected THeriCusBookServiceImpl cusBookService;

    protected THeriCusBookMapper cusBookMapper;
    protected THeriCusRowMapper cusRowMapper;
    protected THeriCusBlockMapper cusBlockMapper;
    protected THeriDevKindRuleMapper devKindRuleMapper;

    protected void setUp() throws Exception {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:cusbookit_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.dataSource.setUser("sa");
        runScript(this.dataSource);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(THeriCusBookMapper.class);
        cfg.addMapper(THeriCusRowMapper.class);
        cfg.addMapper(THeriCusBlockMapper.class);
        cfg.addMapper(THeriDevKindRuleMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(this.dataSource);
        factoryBean.setConfiguration(cfg);
        SqlSessionFactory factory = factoryBean.getObject();

        this.session = new SqlSessionTemplate(factory);
        this.cusBookMapper = this.session.getMapper(THeriCusBookMapper.class);
        this.cusRowMapper = this.session.getMapper(THeriCusRowMapper.class);
        this.cusBlockMapper = this.session.getMapper(THeriCusBlockMapper.class);
        this.devKindRuleMapper = this.session.getMapper(THeriDevKindRuleMapper.class);

        this.cusBookService = new THeriCusBookServiceImpl();
        ReflectionTestUtils.setField(this.cusBookService, "cusBookMapper", this.cusBookMapper);
        ReflectionTestUtils.setField(this.cusBookService, "cusRowMapper", this.cusRowMapper);
        ReflectionTestUtils.setField(this.cusBookService, "cusBlockMapper", this.cusBlockMapper);
        ReflectionTestUtils.setField(this.cusBookService, "devKindRuleMapper", this.devKindRuleMapper);

        seedDeviceKinds();
    }

    /** 门口校验档：四种标准箱型启用，99XX 撤下 */
    private void seedDeviceKinds() {
        seedKind(1L, "20GP", 0);
        seedKind(2L, "40GP", 0);
        seedKind(3L, "40HQ", 0);
        seedKind(4L, "45HQ", 0);
        seedKind(9L, "99XX", 1);
    }

    protected void seedKind(Long id, String kind, int status) {
        com.fc.v2.model.auto.THeriDevKindRule r = new com.fc.v2.model.auto.THeriDevKindRule();
        r.setId(id);
        r.setDeviceKind(kind);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setCreateBy("seed");
        r.setCreateTime(new java.util.Date());
        this.devKindRuleMapper.insert(r);
    }

    private static void runScript(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             Reader r = new InputStreamReader(
                     CusBookITSupport.class.getResourceAsStream("/schema-h2.sql"), "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            for (String sql : sb.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    st.execute(sql);
                }
            }
        }
    }
}
