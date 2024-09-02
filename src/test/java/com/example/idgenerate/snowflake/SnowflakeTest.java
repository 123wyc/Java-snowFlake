package com.example.idgenerate.snowflake;


import com.example.idgenerate.util.SnowFlakeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SnowflakeTest {



    @Test
    public void generateId() {


        System.out.println(SnowFlakeUtil.nextId());

    }
}
