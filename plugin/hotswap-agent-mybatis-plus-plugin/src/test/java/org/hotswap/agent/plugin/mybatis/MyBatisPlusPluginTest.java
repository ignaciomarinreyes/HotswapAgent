package org.hotswap.agent.plugin.mybatis;

import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.hotswap.agent.plugin.mybatis.plus.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Order;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;

public class MyBatisPlusPluginTest extends BaseTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeClass
    public static void setup() throws Exception {
        // create an SqlSessionFactory
        File f = Resources.getResourceAsFile("org/hotswap/agent/plugin/mybatis/PlusMapper1.xml");
        Files.copy(f.toPath(), f.toPath().getParent().resolve("PlusMapper.xml"), StandardCopyOption.REPLACE_EXISTING);
        try (Reader reader = Resources.getResourceAsReader("org/hotswap/agent/plugin/mybatis/mybatis-config.xml")) {
            sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(reader);
        }

        // populate in-memory database
        runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                "org/hotswap/agent/plugin/mybatis/CreateDB.sql");
    }


    @AfterClass
    public static void tearDown() throws Exception {
        swapClasses(PlusUser.class, PlusUser1.class.getName());
        swapClasses(PlusMapper.class, PlusMapper1.class.getName());
    }


    @Test
    @Order(1)
    public void testUserFromXML() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            PlusUser user = mapper.getUserXML("User1");
            assertEquals("User1", user.getName1());
        }
        swapMapper("org/hotswap/agent/plugin/mybatis/PlusMapper2.xml", "PlusMapper.xml");
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            PlusUser user = mapper.getUserXML("User1");
            assertEquals("User2", user.getName1());
        }
    }

    @Test
    @Order(2)
    public void testUserDynamicSql() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            PlusUser user = mapper.selectById(1);
            assertEquals("User1", user.getName1());
        }

        // add a column for
        runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                "org/hotswap/agent/plugin/mybatis/AddColumn.sql");

        // noting change
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            PlusUser user = mapper.selectById(1);
            assertEquals("User1", user.getName1());

            // assert gender not exist
            boolean genderFieldExist = true;
            try {
                user.getClass().getDeclaredField("gender");
            }catch (NoSuchFieldException e) {
                genderFieldExist = false;
            }
            assertFalse(genderFieldExist);
        }

        // after swap, the user should have gender field. and selectById should have gender column
        swapClasses(PlusUser.class, PlusUser2.class.getName());
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            PlusUser user = mapper.selectById(1);
            assertEquals("User1", user.getName1());
            Field gender = user.getClass().getDeclaredField("gender");
            gender.setAccessible(true);
            assertEquals("male", gender.get(user));
        }
    }

    @Test
    @Order(3)
    public void testUserFromAnno() throws Exception {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            // select name2 from users where name1 = #{name1}
            PlusUser user = mapper.getUser("User1");
            assertEquals("User1", user.getName1());
            assertNotNull(user.getId());
        }
        swapClasses(PlusMapper.class, PlusMapper2.class.getName());
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            // select name1 from users where name2 = #{name1}
            PlusMapper mapper = sqlSession.getMapper(PlusMapper.class);
            PlusUser user = mapper.getUser("User1");
            assertEquals("User2", user.getName1());
            assertNull(user.getId());
        }
    }
}
