package com.xxl.job.core.handler.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.xxl.job.core.enums.ExecutorBlockStrategy;
import com.xxl.job.core.enums.ExecutorFailStrategy;
import com.xxl.job.core.enums.ExecutorRouteStrategy;

/**
 * annotation for job handler
 * <BR>使用些功能时xxl-job-admin中必须在com.xxl.job.admin.controller.add
 * <BR>方法上增加@PermessionLimit(limit=false)
 * @author 杨坤国 
 * 2018-5-25 19:06:49
 */
@Target({ElementType.TYPE,ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RegistJobHandler {
    
	/**执行器主键ID	(JobKey.group)查看库xxl-job表xxl_job_qrtz_trigger_group
	 * <br>第一次时请先在控制台增加一个执行器，否则注册会失败
     * @return
     */
//    int jobGroup() default 1;		// 
	
    /**任务执行CRON表达式 【base on quartz】
     * <br>示例：  0 0/2 * * * ?  每2分钟执行一次
     * <BR>必填项
     * @return
     */
    String jobCron();		// 
	
	/**请输入任务描述<BR>必填项
	 * @return
	 */
	public String jobDesc() default "";
	
	/**负责人<BR>必填项
	 * @return
	 */
	public String author() default "";		// 
	
	/**报警邮件
	 * @return
	 */
	public String alarmEmail() default "";	// 

	/**执行器路由策略 ExecutorRouteStrategy.FIRST
	 * <BR>默认第一个
	 * <BR>必填项
	 * @return
	 */
	ExecutorRouteStrategy executorRouteStrategy() default ExecutorRouteStrategy.FIRST;	// 
	
	/**执行器，任务参数
	 * @return
	 */
	public String executorParam() default "";		    // 
	
	/**阻塞处理策略 ExecutorBlockStrategy
	 * <BR>默认单机串行
	 * <BR>必填项
	 * @return
	 */
	public ExecutorBlockStrategy executorBlockStrategy() default ExecutorBlockStrategy.SERIAL_EXECUTION;	// 
	
	/**失败处理策略,默认失败告警 ExecutorFailStrategy
	 * <BR>FAIL_ALARM=失败告警	
	 * <BR>FAIL_RETRY=失败重试
	 * <BR>必填项
	 * @return
	 */
	public ExecutorFailStrategy executorFailStrategy() default ExecutorFailStrategy.FAIL_ALARM;	// 

	/**GLUE类型	#com.xxl.job.core.glue.GlueTypeEnum
	 * 默认Java Bean
	 * <BR>必填项
	 * @return
	 */
//	public String glueType() default "BEAN";		// 

	/**子任务ID，多个逗号分隔
	 * @return
	 */
	public String childJobId() default "";		// 子任务ID，多个逗号分隔
	
	/**是否覆盖上次的Cron配置,true时直接覆盖注册中心里的Cron配置
	 * @return
	 */
	public boolean overWriteCron() default false;
}
