package com.xxl.job.core.enums;


/**
 * @author yangkunguo 20180528
 *
 */
public enum ExecutorBlockStrategy {

    /**
     * ("单机串行")
     */
    SERIAL_EXECUTION,
    /*CONCURRENT_EXECUTION("并行"),*/
    /**
     * ("丢弃后续调度")
     */
    DISCARD_LATER,
    /**
     * ("覆盖之前调度")
     */
    COVER_EARLY;

   
}
