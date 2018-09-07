package com.xxl.job.core.enums;

/**路由策略
 * Created by yangkunguo on 18/5/10.
 */
public enum ExecutorRouteStrategy {

    /**
     * ("FIRST","第一个")
     */
    FIRST,//
    /**
     * ("LAST", "最后一个")
     */
    LAST,//
    /**
     * ("ROUND","轮询")
     */
    ROUND,//
    /**
     * ("RANDOM", "随机")
     */
    RANDOM,//
    /**
     * ("CONSISTENT_HASH", "一致性HASH")
     */
    CONSISTENT_HASH,//
    /**
     * ("LEAST_FREQUENTLY_USED", "最不经常使用")
     */
    LEAST_FREQUENTLY_USED,//
    /**
     * ("LEAST_RECENTLY_USED", "最近最久未使用")
     */
    LEAST_RECENTLY_USED,//
    /**
     * ("FAILOVER", "故障转移")
     */
    FAILOVER,
    /**
     * ("BUSYOVER", "忙碌转移")
     */
    BUSYOVER,
    /**
     * ("SHARDING_BROADCAST", "分片广播")
     */
    SHARDING_BROADCAST;

    
    
}
