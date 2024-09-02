package com.example.idgenerate.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.Snowflake;

/**
 * @Author: Robin
 * @Description:
 * @Date: create in 2020/9/6 0006 21:44
 */
public class SnowFlakeUtil {
    /**
     * 派号器workid：0~31
     * 机房datacenterid：0~31
     */
    private static Snowflake snowflake;

    public static void init(long workerId, long dataCenterId) {
        if (snowflake == null) {
            snowflake = new Snowflake(DateUtil.parse("2022-10-01"), workerId, dataCenterId, true);
            Singleton.put(snowflake);
        }
    }

    public static void clear() {
        snowflake = null;
    }

    public static Long nextId() {
        assert snowflake != null;
        return snowflake.nextId();
    }
}
