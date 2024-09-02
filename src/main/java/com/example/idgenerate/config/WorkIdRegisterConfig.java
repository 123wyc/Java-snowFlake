package com.example.idgenerate.config;


import com.example.idgenerate.util.SnowFlakeUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class WorkIdRegisterConfig {

    @Value("${spring.application.name}")
    private String  applicationName;

    private final static String MACHINE_ID_kEY = "machine_id_key:";
    private RedissonClient redissonClient;

    /**
     * 机器id
     */
    public static Integer machineId;

    /**
     * 数据中心ID
     */
    public static Integer dataCenterId;

    /**
     * 本地ip地址
     */
    private static String localIp;
    private ScheduledExecutorService scheduled;

    public WorkIdRegisterConfig(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取ip地址
     *
     * @return
     * @throws UnknownHostException
     */
    private String getIPAddress() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address.getHostAddress();
    }


    /**
     * hash机器IP初始化一个机器ID
     */
    @PostConstruct
    public void initMachineId() throws Exception {
        localIp = getIPAddress() + " : " + applicationName;
        //注册机器ID
        registerMachineId();
        //启动一个线程更新超时时间
        scheduleUpdateExpTime();
        // 生产SnakeID对象
        SnowFlakeUtil.init(machineId, dataCenterId);

        log.info("初始化机器ID ip:{}, machine_id:{}, data_center_id:{}", localIp, machineId, dataCenterId);
    }

    /**
     * 容器销毁前清除注册记录
     */
    @PreDestroy
    public void destroyMachineId() {
        if (scheduled != null) {
            scheduled.shutdown();
        }
        if (machineId == null || dataCenterId == null || localIp == null || redissonClient == null) {
            return;
        }

        RBucket<String> bucket = redissonClient.getBucket(machineIdKey(dataCenterId, machineId));
        if (bucket.get().equals(localIp)) {
            bucket.delete();
            log.info("清除注册记录 local_ip :{},dataCenter_id :{},machine_id :{}", localIp, dataCenterId, machineId);
        }
        SnowFlakeUtil.clear();
    }


    /**
     * 主方法：获取一个机器id
     *
     * @return
     */
    public void registerMachineId() throws Exception {
        try {
            //判断0~31这个区间段的机器IP是否被占满
            for (int dc = 0; dc <= 31; dc++) {
                for (int work = 0; work <= 31; work++) {
                    boolean ok = redissonClient.getBucket(machineIdKey(dc, work))
                            .trySet(localIp, 10, TimeUnit.SECONDS);
                    if (ok) {
                        dataCenterId = dc;
                        machineId = work;

                        return;
                    }
                }
            }
            throw new RuntimeException("机器ID已经被占满");
        } catch (Exception e) {
            log.error("创建ID生成器失败", e);
            throw new Exception("创建ID生成器失败", e);
        }
    }

    private void scheduleUpdateExpTime() {
        scheduled = Executors.newSingleThreadScheduledExecutor();
        scheduled.scheduleAtFixedRate(() -> {
            RBucket<String> bucket = redissonClient.getBucket(machineIdKey(dataCenterId, machineId));
            if (bucket.get().equals(localIp)) {
                bucket.expire(10, TimeUnit.SECONDS);
            } else {
                log.warn("更新机器ID超时时间失败,local_ip:{} 于 dataCenter_id:{},machine_id:{} 中注册 ip {} 不一致 ",
                        localIp, dataCenterId, machineId, bucket.get());
                try {
                    log.warn("尝试重新注册机器ID");
                    //注册机器ID
                    registerMachineId();
                    // 生产SnakeID对象
                    SnowFlakeUtil.init(machineId, dataCenterId);
                } catch (Exception e) {
                    log.error("重新注册机器ID失败", e);
                    destroyMachineId();
                }
            }
        }, 1, 3, TimeUnit.SECONDS);

    }

    private String machineIdKey(int dataCenterId, Integer machineId) {
        return MACHINE_ID_kEY + "D_" + dataCenterId + "_M_" + machineId;
    }

}
