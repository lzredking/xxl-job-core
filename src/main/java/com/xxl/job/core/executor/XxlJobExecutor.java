package com.xxl.job.core.executor;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.xxl.job.core.biz.model.RegistryParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.enums.RegistryConfig;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.JobHandler;
import com.xxl.job.core.handler.annotation.RegistJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.rpc.netcom.NetComClientProxy;
import com.xxl.job.core.rpc.netcom.NetComServerFactory;
import com.xxl.job.core.thread.JobLogFileCleanThread;
import com.xxl.job.core.thread.JobThread;
import com.xxl.job.core.util.HttpClientUtil;
import com.xxl.job.core.util.JacksonUtil;
import com.xxl.job.core.util.NetUtil;

/**
 * Created by xuxueli on 2016/3/2 21:14.
 */
public class XxlJobExecutor implements ApplicationContextAware {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobExecutor.class);

    // ---------------------- param ----------------------
    private String adminAddresses;
    private String appName;
    private String ip;
    private int port;
    private String accessToken;
    private String logPath;
    private int logRetentionDays;

    
    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }
    public void setAppName(String appName) {
        this.appName = appName;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    // ---------------------- applicationContext ----------------------
    private static ApplicationContext applicationContext;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    // ---------------------- start + stop ----------------------
    public void start() throws Exception {
        // init admin-client
        initAdminBizList(adminAddresses, accessToken);

        // init executor-jobHandlerRepository
        initJobHandlerRepository(applicationContext);

        // init logpath
        XxlJobFileAppender.initLogPath(logPath);

        //Regist Job ykg add 201805
        startRegistJobThread();
        
        // init executor-server
        initExecutorServer(port, ip, appName, accessToken);

        // init JobLogFileCleanThread
        JobLogFileCleanThread.getInstance().start(logRetentionDays);
    }
    public void destroy(){
        // destory JobThreadRepository
        if (JobThreadRepository.size() > 0) {
            for (Map.Entry<Integer, JobThread> item: JobThreadRepository.entrySet()) {
                removeJobThread(item.getKey(), "Web容器销毁终止");
            }
            JobThreadRepository.clear();
        }

        // destory executor-server
        stopExecutorServer();

        // destory JobLogFileCleanThread
        JobLogFileCleanThread.getInstance().toStop();
    }


    // ---------------------- admin-client ----------------------
    private static List<AdminBiz> adminBizList;
    private static void initAdminBizList(String adminAddresses, String accessToken) throws Exception {
        if (adminAddresses!=null && adminAddresses.trim().length()>0) {
            for (String address: adminAddresses.trim().split(",")) {
                if (address!=null && address.trim().length()>0) {
                    String addressUrl = address.concat(AdminBiz.MAPPING);
                    AdminBiz adminBiz = (AdminBiz) new NetComClientProxy(AdminBiz.class, addressUrl, accessToken).getObject();
                    if (adminBizList == null) {
                        adminBizList = new ArrayList<AdminBiz>();
                    }
                    adminBizList.add(adminBiz);
                }
            }
        }
    }
    public static List<AdminBiz> getAdminBizList(){
        return adminBizList;
    }


    // ---------------------- executor-server(jetty) ----------------------
    private NetComServerFactory serverFactory = new NetComServerFactory();
    private void initExecutorServer(int port, String ip, String appName, String accessToken) throws Exception {
        // valid param
        port = port>0?port: NetUtil.findAvailablePort(9999);

        // start server
        NetComServerFactory.putService(ExecutorBiz.class, new ExecutorBizImpl());   // rpc-service, base on jetty
        NetComServerFactory.setAccessToken(accessToken);
        serverFactory.start(port, ip, appName); // jetty + registry
    }
    private void stopExecutorServer() {
        serverFactory.destroy();    // jetty + registry + callback
    }


    // ---------------------- job handler repository ----------------------
    private static ConcurrentHashMap<String, IJobHandler> jobHandlerRepository = new ConcurrentHashMap<String, IJobHandler>();
    public static IJobHandler registJobHandler(String name, IJobHandler jobHandler){
        logger.info(">>>>>>>>>>> xxl-job register jobhandler success, name:{}, jobHandler:{}", name, jobHandler);
        return jobHandlerRepository.put(name, jobHandler);
    }
    public static IJobHandler loadJobHandler(String name){
        return jobHandlerRepository.get(name);
    }
    private static void initJobHandlerRepository(ApplicationContext applicationContext){
        if (applicationContext == null) {
            return;
        }

        // init job handler action
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(JobHandler.class);

        if (serviceBeanMap!=null && serviceBeanMap.size()>0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler){
                    String name = serviceBean.getClass().getAnnotation(JobHandler.class).value();
                    IJobHandler handler = (IJobHandler) serviceBean;
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler naming conflicts.");
                    }
                    registJobHandler(name, handler);
                }
            }
        }
    }


    // ---------------------- job thread repository ----------------------
    private static ConcurrentHashMap<Integer, JobThread> JobThreadRepository = new ConcurrentHashMap<Integer, JobThread>();
    public static JobThread registJobThread(int jobId, IJobHandler handler, String removeOldReason){
        JobThread newJobThread = new JobThread(jobId, handler);
        newJobThread.start();
        logger.info(">>>>>>>>>>> xxl-job regist JobThread success, jobId:{}, handler:{}", new Object[]{jobId, handler});

        JobThread oldJobThread = JobThreadRepository.put(jobId, newJobThread);	// putIfAbsent | oh my god, map's put method return the old value!!!
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }

        return newJobThread;
    }
    public static void removeJobThread(int jobId, String removeOldReason){
        JobThread oldJobThread = JobThreadRepository.remove(jobId);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }
    }
    public static JobThread loadJobThread(int jobId){
        JobThread jobThread = JobThreadRepository.get(jobId);
        return jobThread;
    }
    ////////////////////////////////////////////////////以下内容为新加/////////////////////////////////////////////////////////////////////
    private volatile boolean toStop = false;
    private Thread registryThread;
    private int jobGroup=0;
    
    /**
     * 自动注册执行器JobGroup和作业任务JobHandler
     * @author yangkunguo 20180528
     */
    private void startRegistJobThread(){
    	registryThread = new Thread(new Runnable() {
            @Override
            public void run() {

                // registry
                while (!toStop) {
                    try {
                    	initRegistJob(adminAddresses, appName,applicationContext);
                    	logger.info(">>>>>>>>>>> xxl-job Job..... registry success, adminAddresses:{}, appName:{}", new Object[]{adminAddresses, appName});
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                    
                    try {
                        TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                }

            }
        });
        registryThread.setDaemon(false);
        registryThread.start();
    }
    public void toStop() {
        toStop = true;
        System.out.println(registryThread);
        
        logger.info(">>>>>>>>>>> xxl-job, Job..... registry thread destory.");
    }
    /** 自动注册执行器JobGroup和作业任务JobHandler
     * @param adminAddresses
     * @param appName
     * @param applicationContext
     * @throws Exception
     * @author yangkunguo 20180528
     */
    private  void initRegistJob(String adminAddresses,String appName,ApplicationContext applicationContext) throws Exception{
    	
    	if (applicationContext == null) {
            return;
        }
    	// init job handler action
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(RegistJobHandler.class);

        if (serviceBeanMap!=null && serviceBeanMap.size()>0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler){
                	RegistJobHandler jobhandler = serviceBean.getClass().getAnnotation(RegistJobHandler.class);
                	
                	IJobHandler handler = (IJobHandler) serviceBean;
                	
                    String name=serviceBean.getClass().getPackage().getName()+"."+serviceBean.getClass().getSimpleName();
                    
//                    if (!CronExpression.isValidExpression(jobhandler.jobCron())) {
//            			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("jobinfo_field_cron_unvalid") );
//            		}
                	if (jobhandler.jobCron()==null || jobhandler.jobCron().trim().equals("")) {
            			logger.error(serviceBean.getClass().getSimpleName()+" please input jobCron");
            		}
            		if (jobhandler.jobDesc()==null ) {
            			logger.error(serviceBean.getClass().getSimpleName()+" please input jobDesc");
            		}
            		if (jobhandler.author()==null) {
            			logger.error(serviceBean.getClass().getSimpleName()+"  please input jobDesc");
            		}
            		
            		StringBuilder group= new StringBuilder();
            		group.append("appName=").append(URLEncoder.encode(appName,"UTF-8"))
            		.append("&title=").append(URLEncoder.encode(appName+"-AUTO","UTF-8"))
            		.append("&order=1")
            		.append("&addressType=0");
            		
            		
//            		XxlJobInfo jobInfo=new XxlJobInfo();xxl-admin不支持对象
//            		jobInfo.setJobGroup(jobhandler.jobGroup());
//            		jobInfo.setJobDesc(jobhandler.jobDesc());
//            		jobInfo.setJobCron(jobhandler.jobCron());
//            		jobInfo.setGlueType(jobhandler.glueType());
//            		jobInfo.setExecutorHandler(jobhandler.executorHandler());
//            		jobInfo.setExecutorParam(jobhandler.executorParam());
//            		jobInfo.setExecutorBlockStrategy(jobhandler.executorBlockStrategy());
//            		jobInfo.setExecutorFailStrategy(jobhandler.executorFailStrategy());
//            		jobInfo.setAuthor(jobhandler.author());
//            		jobInfo.setAlarmEmail(jobhandler.alarmEmail());
            		
//            		String json=JacksonUtil.writeValueAsString(jobInfo);
//            		System.out.println(url.toString());
            		
            		
//            		String login=adminAddresses+"/login?userName=admin&password=123456&ifRemember=false";
//            		byte[] btlog=HttpClientUtil.postRequest(login,null);
//        			System.out.println(">>>>>>>> 执行器自动注册："+new String(btlog));
            		
            		//注册执行器
            		if(jobGroup==0){
            			
//            			getAdminBizList().get(0).registry(registryParam)
            			String reqGroupUrl=adminAddresses+"/jobgroup/save?"+group.toString();
            			System.out.println(reqGroupUrl);
            			byte[] btg=HttpClientUtil.postRequest(reqGroupUrl,null);
            			System.out.println(">>>>>>>> 执行器自动注册："+new String(btg));
            			ReturnT<String> res=JacksonUtil.readValue(new String(btg), ReturnT.class);
            			
            			if(res.getCode()==ReturnT.SUCCESS_CODE)
            				jobGroup=Integer.valueOf(res.getMsg());
            			else
            				logger.error("执行器自动注册失败。。。。。请手动在控制台添加执行器。。。。。。");
            		}
            		
            		//注册任务
            		StringBuilder job= new StringBuilder();
            		job.append("jobGroup=").append(jobGroup)
            		.append("&jobDesc=").append(URLEncoder.encode(jobhandler.jobDesc(),"UTF-8"))
            		.append("&jobCron=").append(URLEncoder.encode(jobhandler.jobCron(),"UTF-8"))
            		.append("&glueType=").append("BEAN")
            		.append("&executorHandler=").append(URLEncoder.encode(name,"UTF-8"))
            		.append("&executorParam=").append(URLEncoder.encode(jobhandler.executorParam(),"UTF-8"))
            		.append("&executorBlockStrategy=").append(jobhandler.executorBlockStrategy())
            		.append("&executorFailStrategy=").append(jobhandler.executorFailStrategy())
            		.append("&executorRouteStrategy=").append(jobhandler.executorRouteStrategy())
            		.append("&author=").append(URLEncoder.encode(jobhandler.author(),"UTF-8"))
            		.append("&alarmEmail=").append(URLEncoder.encode(jobhandler.alarmEmail(),"UTF-8"));
            		
            		String reqUrl=adminAddresses+"/jobinfo/regist?"+job.toString();
            		System.out.println(reqUrl);
            		byte[] bt=HttpClientUtil.postRequest(reqUrl,null);
            		logger.info(new String(bt));
            		System.out.println(">>>>>>>> 任务自动注册："+new String(bt));
            		ReturnT<String> res=JacksonUtil.readValue(new String(bt), ReturnT.class);
            		if(res.getCode()==ReturnT.FAIL_CODE)
            			logger.error("任务自动注册失败。。。。。。请手动在控制台添加任务。。。。。");
            		
            		//
            		if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler naming conflicts.");
                    }
                    registJobHandler(name, handler);
                }
            }
        }
        toStop();
    }
    
    
}
