package com.tihai.service.superstar.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tihai.common.*;
import com.tihai.constant.GlobalConstant;
import com.tihai.constant.JobTypeConstant;
import com.tihai.constant.RetryConstant;
import com.tihai.domain.chaoxing.SuperStarLog;
import com.tihai.domain.chaoxing.SuperStarTask;
import com.tihai.domain.chaoxing.WkUser;
import com.tihai.dubbo.dto.CourseSubmitTaskDTO;
import com.tihai.dubbo.pojo.course.Course;
import com.tihai.enums.BizCodeEnum;
import com.tihai.enums.WkTaskStatusEnum;
import com.tihai.exception.BusinessException;
import com.tihai.factory.CustomThreadFactory;
import com.tihai.factory.PriorityRejectPolicy;
import com.tihai.manager.RollBackManager;
import com.tihai.mapper.SuperStarMapper;
import com.tihai.properties.StudyProperties;
import com.tihai.properties.ThreadPoolProperties;
import com.tihai.queue.PriorityTaskWrapper;
import com.tihai.service.superstar.SuperStarLogService;
import com.tihai.service.superstar.SuperStarLoginService;
import com.tihai.service.superstar.SuperStarTaskService;
import com.tihai.service.superstar.SuperStarUserService;
import com.tihai.utils.CourseUtil;
import com.tihai.utils.ServerInfoUtil;
import lombok.extern.slf4j.Slf4j;
import ma.glasnost.orika.MapperFacade;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;


/**
 * @Copyright : DuanInnovator
 * @Description : 超星学习通-任务启动和实现
 * @Author : DuanInnovator
 * @CreateTime : 2025/4/26
 * @Link : <a href="https://github.com/DuanInnovator/TiHaiWuYou-Admin/tree/mine-admin">...</a>
 **/
@Service
@Slf4j
public class SuperStarTaskServiceImpl extends ServiceImpl<SuperStarMapper, SuperStarTask> implements SuperStarTaskService {

    @Autowired
    private MapperFacade mapperFacade;

    @Autowired
    private ThreadPoolExecutor taskExecutor;

    @Autowired
    private SuperStarUserService userService;

    @Autowired
    private CourseUtil courseUtil;
    @Autowired(required=false) private org.springframework.context.ApplicationContext accountContext;
    private final ThreadLocal<CourseUtil> workerCourseUtil=new ThreadLocal<>();
    private CourseUtil taskCourseUtil(){CourseUtil u=workerCourseUtil.get();return u==null?courseUtil:u;}

    @Autowired
    private SuperStarLogService superStarLogService;

    @Autowired
    private SuperStarLoginService loginService;

    @Autowired
    private RollBackManager rb;

    @Autowired
    private ServerInfoUtil serverInfoUtil;

    @Autowired
    private ThreadPoolProperties config;

    @Autowired
    private StudyProperties studyProperties;

    @Autowired
    private com.tihai.utils.LiveProgressTracker liveProgress;


    private final Set<String> scheduledTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> runningTaskIds = ConcurrentHashMap.newKeySet();

    private final Map<String,Thread> workerThreads = new ConcurrentHashMap<>();
    private final Map<String,Runnable> queuedJobs = new ConcurrentHashMap<>();
    private final Set<String> stopRequests = ConcurrentHashMap.newKeySet();

    public synchronized String stopTask(String taskId) {
        SuperStarTask task=this.getById(taskId);
        if(task==null)throw new BusinessException(404,"任务不存在");
        if(Integer.valueOf(4).equals(task.getStatus())&&!scheduledTaskIds.contains(taskId))return "任务已经完成";
        stopRequests.add(taskId);task.setStatus(WkTaskStatusEnum.PAUSED.getCode());this.updateById(task);
        liveProgress.cancel(taskId);
        Runnable queued=queuedJobs.get(taskId);
        if(queued!=null && taskExecutor.remove(queued)){queuedJobs.remove(taskId);scheduledTaskIds.remove(taskId);stopRequests.remove(taskId);return "任务已停止";}
        Thread worker=workerThreads.get(taskId);if(worker!=null)worker.interrupt();
        if(!scheduledTaskIds.contains(taskId)){stopRequests.remove(taskId);return "任务已停止";}
        return "正在停止，请等待后台执行退出";
    }
    public synchronized String deleteTask(String taskId) {
        if(scheduledTaskIds.contains(taskId))throw new BusinessException(409,"请先停止任务，等待停止完成后再删除");
        SuperStarTask task=this.getById(taskId);if(task==null)throw new BusinessException(404,"任务不存在");
        if(!this.removeById(taskId))throw new BusinessException(500,"任务删除失败");return "任务已删除，学习通课程和本机日志未改动";
    }
    public String executionState(String taskId) {
        if(stopRequests.contains(taskId)&&scheduledTaskIds.contains(taskId))return "stopping";
        return runningTaskIds.contains(taskId) ? "running" : scheduledTaskIds.contains(taskId) ? "queued" : "idle";
    }

    public synchronized String resumeTask(String taskId) throws NacosException {
        SuperStarTask task = this.getById(taskId);
        if (task == null) throw new BusinessException(404, "任务不存在");
        if (scheduledTaskIds.contains(taskId)) return "任务已在执行，无需重复提交";
        prepareResume(task);
        startChaoxingTask();
        return "任务已恢复，正在继续处理";
    }

    public synchronized String rescanTask(String taskId) throws NacosException {
        if(scheduledTaskIds.contains(taskId))throw new BusinessException(409,"请先停止当前任务再重新检查");
        SuperStarTask task=this.getById(taskId);if(task==null)throw new BusinessException(404,"任务不存在");
        SuperStarLog checkpoint=createNewLog(task);checkpoint.setCurrentChapterIndex(0);checkpoint.setCurrentProgress(BigDecimal.ZERO);checkpoint.setRemark("从第一章重新检查未完成任务");superStarLogService.saveOrUpdate(checkpoint);
        task.setStatus(0);task.setRetryCount(0);this.updateById(task);startChaoxingTask();return "已从第一章重新检查，平台标记已完成的任务将跳过";
    }
    private void prepareResume(SuperStarTask task) {
        SuperStarLog latest = superStarLogService.getLatestLogByLoginAccount(task.getLoginAccount(), task.getCourseName());
        if (Integer.valueOf(4).equals(task.getStatus()) || (latest != null && Integer.valueOf(4).equals(latest.getStatus()) && latest.getCurrentProgress() != null && latest.getCurrentProgress().compareTo(BigDecimal.valueOf(100)) >= 0))
            throw new BusinessException(409, "该课程任务已完成，无需重复提交");
        task.setStatus(WkTaskStatusEnum.PENDING.getCode());
        task.setRetryCount(0);
        this.updateById(task);
    }

    private volatile boolean isShuttingDown = false;
    private final Object shutdownLock = new Object();


    // 日志批量保存的缓冲队列
    private final BlockingQueue<SuperStarLog> logQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Long, SuperStarLog> logMap = new ConcurrentHashMap<>();

    public SuperStarTaskServiceImpl() throws NacosException {
    }

    public void shutdownThreadPoolGracefully() throws NacosException {
        // 提前检查减少同步块竞争
        if (isShuttingDown || taskExecutor.isShutdown()) {
            return;
        }

        synchronized (shutdownLock) {
            // 再次检查（双重检查锁模式）
            if (isShuttingDown || taskExecutor.isShutdown()) {
                return;
            }
            isShuttingDown = true;

            try {
                // 1. 停止接受新任务
                taskExecutor.shutdown();
                log.info("线程池开始关闭，等待现有任务完成...");

                // 2. 分阶段等待
                // 第一阶段：等待正常完成
                if (!taskExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                    log.warn("等待超时，尝试强制停止剩余任务...");

                    // 第二阶段：强制停止
                    List<Runnable> unfinishedTasks = taskExecutor.shutdownNow();

                    // 3. 处理未完成任务
                    if (!unfinishedTasks.isEmpty()) {
                        log.warn("有{}个任务被强制停止，开始回滚状态...", unfinishedTasks.size());
                        processUnfinishedTasks(unfinishedTasks);
                    }

                    // 最后检查
                    if (!taskExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                        log.error("线程池最终未能完全关闭");
                    }
                }
            } catch (InterruptedException e) {
                // 处理中断
                List<Runnable> unfinishedTasks = taskExecutor.shutdownNow();
                if (!unfinishedTasks.isEmpty()) {
                    processUnfinishedTasks(unfinishedTasks);
                }
                Thread.currentThread().interrupt();
                throw new NacosException(NacosException.SERVER_ERROR, "线程池关闭被中断");
            } finally {
                log.info("线程池关闭完成，当前状态: isShutdown={}, isTerminated={}",
                        taskExecutor.isShutdown(), taskExecutor.isTerminated());

            }
        }
    }

    private void processUnfinishedTasks(List<Runnable> unfinishedTasks) {
        unfinishedTasks.forEach(task -> {
            if (task instanceof PriorityTaskWrapper) {
                String taskId = ((PriorityTaskWrapper) task).getTaskId();
                try {
                    SuperStarTask dbTask = this.getById(taskId);
                    if (dbTask != null &&
                            (WkTaskStatusEnum.PROCESSING.getCode().equals(dbTask.getStatus()) ||
                                    WkTaskStatusEnum.EXAM.getCode().equals(dbTask.getStatus()))) {
                        dbTask.setStatus(WkTaskStatusEnum.PAUSED.getCode());
                        this.updateById(dbTask);
                    }
                } catch (Exception e) {
                    log.error("回滚任务状态失败, taskId={}", taskId, e);
                }
            }
        });
    }

    public void restartThreadPoolAndTasks() {
        synchronized (shutdownLock) {
            if (!taskExecutor.isShutdown()) {
                return;
            }

            BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(config.getQueueCapacity());

            // 重新构建线程池
            taskExecutor = new ThreadPoolExecutor(
                    config.getCoreSize(),
                    config.getMaxSize(),
                    config.getKeepAlive(),
                    TimeUnit.SECONDS,
                    queue,
                    new CustomThreadFactory(config.getThreadNamePrefix()),
                    new PriorityRejectPolicy()
            );
            taskExecutor.allowCoreThreadTimeOut(config.isAllowCoreThreadTimeout());
            this.startChaoxingTask();
        }
    }

    @PostConstruct
    public void init() {
        // 启动一个单独的线程来处理日志的批量保存
        new Thread(this::batchSaveLogs).start();
    }

    private void batchSaveLogs() {
        List<SuperStarLog> batch = new ArrayList<>(100);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SuperStarLog log = logQueue.poll(1, TimeUnit.SECONDS);
                if (log != null) {
                    batch.add(log);
                    logMap.put(log.getId(), log); // 合并更新
                }
                // 批量保存条件：达到100条或超时
                if (batch.size() >= 100 || (log == null && !batch.isEmpty())) {
                    List<SuperStarLog> toSave = new ArrayList<>(logMap.values());

                    if (log != null) {
                        log.setMachineNum(serverInfoUtil.getCurrentServerInstance());
                    }
                    superStarLogService.saveOrUpdateBatch(toSave);
                    logMap.clear();
                    batch.clear();

                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (NacosException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private SuperStarLog createNewLog(SuperStarTask task) {
        SuperStarLog log = mapperFacade.map(task, SuperStarLog.class);
        log.setId(null);
        log.setStartTime(LocalDateTime.now());
        log.setStatus(WkTaskStatusEnum.PROCESSING.getCode());
        // 首次插入，获取自增ID
        superStarLogService.save(log);
        return log;
    }

    /**
     * 根据账号和课程id获取超星学习任务
     *
     * @param loginAccount 账号
     * @param courseId     课程id
     */
    @Override
    public SuperStarTask getSuperStarTask(String loginAccount, String courseId) {
        LambdaQueryWrapper<SuperStarTask> superStarTaskLambdaQueryWrapper = new LambdaQueryWrapper<>();
        superStarTaskLambdaQueryWrapper.eq(SuperStarTask::getLoginAccount, loginAccount);
        superStarTaskLambdaQueryWrapper.eq(SuperStarTask::getCourseId, courseId);
        return this.getOne(superStarTaskLambdaQueryWrapper);
    }

    /**
     * 根据账号和课程id获取超星学习任务
     *
     * @param loginAccount 账号
     * @param courseName   课程名
     */
    @Override
    public SuperStarTask getSuperStarTaskByCourseName(String loginAccount, String courseName) {
        LambdaQueryWrapper<SuperStarTask> superStarTaskLambdaQueryWrapper = new LambdaQueryWrapper<>();
        superStarTaskLambdaQueryWrapper.eq(SuperStarTask::getLoginAccount, loginAccount);
        superStarTaskLambdaQueryWrapper.eq(SuperStarTask::getCourseName, courseName);
        return this.getOne(superStarTaskLambdaQueryWrapper);
    }

    /**
     * 添加任务到等待队列
     *
     * @param courseSubmitTaskDTO 任务信息
     * @throws NacosException nacos异常
     */
    public synchronized void addChaoXingTask(CourseSubmitTaskDTO courseSubmitTaskDTO) throws NacosException {
        SuperStarTask task = null;
        if (courseSubmitTaskDTO.getCourseId() == null) {
            task = getSuperStarTaskByCourseName(courseSubmitTaskDTO.getLoginAccount(), courseSubmitTaskDTO.getCourseName());
        } else {
            task = getSuperStarTask(courseSubmitTaskDTO.getLoginAccount(), courseSubmitTaskDTO.getCourseId());
        }
        if (task == null) {
            SuperStarTask chaoXingTask = mapperFacade.map(courseSubmitTaskDTO, SuperStarTask.class);
            chaoXingTask.setStatus(WkTaskStatusEnum.PENDING.getCode());
            chaoXingTask.setId(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))); //当前version使用UUID
            chaoXingTask.setPriority(1);
            chaoXingTask.setCreatTime(LocalDateTime.now());
            chaoXingTask.setMachineNum(serverInfoUtil.getCurrentServerInstance());
            this.baseMapper.insert(chaoXingTask);
        } else {
            if (scheduledTaskIds.contains(task.getId())) return;
            task.setPassword(courseSubmitTaskDTO.getPassword());
            prepareResume(task);
        }
        //TODO 这里似乎不太合理,等待后续优化.......
        startChaoxingTask();
    }

    /**
     * 超星学习任务启动
     */
    @Override
    public synchronized void startChaoxingTask() {
        LambdaQueryWrapper<SuperStarTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SuperStarTask::getStatus, WkTaskStatusEnum.PENDING.getCode());
        wrapper.le(SuperStarTask::getRetryCount, RetryConstant.DEFAULT_RETRY_COUNT);
        wrapper.orderByDesc(SuperStarTask::getPriority);
        List<SuperStarTask> tasks = list(wrapper);
//        executeCourseTask(tasks.get(0));

        tasks.forEach(task -> {
            if (!scheduledTaskIds.add(task.getId())) return;
            task.setStatus(WkTaskStatusEnum.QUEUE.getCode());
            try {
                task.setMachineNum(serverInfoUtil.getCurrentServerInstance());
            } catch (NacosException e) {
                throw new RuntimeException(e);
            }
            boolean isUpdated = this.updateById(task);
            Runnable taskWrapper = new PriorityTaskWrapper(() -> {
                runningTaskIds.add(task.getId());
                workerThreads.put(task.getId(),Thread.currentThread());
                liveProgress.begin(task.getId());
                try {
                    if(stopRequests.contains(task.getId()))throw new CancellationException();
                    liveProgress.checkCancelled();
                    task.setStatus(WkTaskStatusEnum.PROCESSING.getCode());
                    this.updateById(task);
                    if(accountContext!=null){CourseUtil isolated=new CourseUtil();accountContext.getAutowireCapableBeanFactory().autowireBean(isolated);workerCourseUtil.set(isolated);}
                    executeCourseTask(task);
                    SuperStarLog latest = superStarLogService.getLatestLogByLoginAccount(task.getLoginAccount(), task.getCourseName());
                    if (latest != null && (Integer.valueOf(4).equals(latest.getStatus()) || Integer.valueOf(5).equals(latest.getStatus()))) {
                        task.setStatus(latest.getStatus());
                        if(Integer.valueOf(4).equals(latest.getStatus()))liveProgress.completed();
                        this.updateById(task);
                    }
                } catch (Exception e) {
                    if(stopRequests.contains(task.getId()) || Thread.currentThread().isInterrupted() || e instanceof CancellationException) {
                        task.setStatus(WkTaskStatusEnum.PAUSED.getCode());
                    } else if (task.getRetryCount() == RetryConstant.DEFAULT_RETRY_COUNT) {
                        task.setStatus(WkTaskStatusEnum.ABNORMAL.getCode());
                    } else {
                        task.setRetryCount(task.getRetryCount() + 1);
                        task.setStatus(WkTaskStatusEnum.PENDING.getCode());
                    }
                    this.updateById(task);
                } finally {
                    if(stopRequests.contains(task.getId())){task.setStatus(WkTaskStatusEnum.PAUSED.getCode());this.updateById(task);}
                    workerThreads.remove(task.getId());queuedJobs.remove(task.getId());
                    stopRequests.remove(task.getId());
                    workerCourseUtil.remove();liveProgress.release();
                    runningTaskIds.remove(task.getId());
                    scheduledTaskIds.remove(task.getId());
                    Thread.interrupted();
                }
            }, task.getPriority(), task.getId());
            queuedJobs.put(task.getId(),taskWrapper);

            try { taskExecutor.execute(taskWrapper); }
            catch (RuntimeException e) {
                scheduledTaskIds.remove(task.getId());queuedJobs.remove(task.getId());
                task.setStatus(WkTaskStatusEnum.PENDING.getCode());
                this.updateById(task);
                throw e;
            }
        });
    }

    /**
     * 执行超星学习任务
     *
     * @param task 超星学习任务
     */
    public void executeCourseTask(SuperStarTask task) {
        WkUser user = userService.getUserByAccount(task.getLoginAccount());
        SuperStarLog log = superStarLogService.getLatestLogByLoginAccount(task.getLoginAccount(), task.getCourseName());

        if (log == null) {
            log = createNewLog(task);
        } else {
            log.setStartTime(LocalDateTime.now());
            log.setEndTime(null);
            log.setStatus(WkTaskStatusEnum.PROCESSING.getCode());
            log.setErrorMessage(null);
            superStarLogService.updateLog(log);
        }


        if (user == null || user.getCookies() == null) {
            if (task.getRetryCount() < RetryConstant.DEFAULT_RETRY_COUNT) {
                task.setStatus(WkTaskStatusEnum.PENDING.getCode());
                task.setRetryCount(task.getRetryCount() + 1);
            } else {
                task.setStatus(WkTaskStatusEnum.ABNORMAL.getCode());
            }
            WkUser wkUser = new WkUser();
            wkUser.setAccount(task.getLoginAccount());
            wkUser.setPassword(task.getPassword());
            loginService.login(wkUser, true);
            this.updateById(task);
            log.setStatus(WkTaskStatusEnum.ABNORMAL.getCode());
            log.setErrorMessage("用户未登录");
            logQueue.offer(log);
            return;
        }

        try {
            Course readyCourse = new Course();
            taskCourseUtil().setAccount(task.getLoginAccount());
            taskCourseUtil().setCookies(user.getCookies());
            if (task.getCourseId() != null) {
                readyCourse = taskCourseUtil().getCourseList().stream()
                        .filter(course -> course.getCourseId().equals(task.getCourseId()))
                        .findFirst().orElse(null);
            } else if (task.getCourseName() != null) {
                List<Course> courseList = taskCourseUtil().getCourseList();
                 readyCourse = Optional.ofNullable(courseList)
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(course -> course != null && task != null)
                        .filter(course -> {
                            // 标准化处理
                            String dbTitle = StringUtils.normalizeSpace(course.getTitle());
                            String taskName = StringUtils.normalizeSpace(task.getCourseName());

                            return dbTitle.equalsIgnoreCase(taskName);
                        })
                        .findFirst()
                        .orElse(null);
            }

            if (readyCourse == null) {
                loginService.login(user, true);
                log.setStatus(WkTaskStatusEnum.ABNORMAL.getCode());
                log.setErrorMessage(GlobalConstant.COURSE_INFO_GET_FAIL);
                logQueue.offer(log);
                return;
            }

            List<CoursePoint> pointList = taskCourseUtil().getCoursePoint(
                    readyCourse.getCourseId(), readyCourse.getClazzId(), readyCourse.getCpi());

            processCoursePoints(task, log, readyCourse, pointList);
        } catch (Exception e) {
            liveProgress.checkCancelled();
            log.setStatus(WkTaskStatusEnum.ABNORMAL.getCode());
            log.setErrorMessage("系统异常");
            logQueue.offer(log);
        }
    }

    void processCoursePoints(SuperStarTask task,SuperStarLog log,Course readyCourse,List<CoursePoint> pointList) {
        List<ChapterPoint> chapters=new ArrayList<>();
        if(pointList!=null)for(CoursePoint group:pointList)if(group!=null&&group.getPoints()!=null)chapters.addAll(group.getPoints());
        if(chapters.isEmpty()){log.setStatus(5);log.setErrorMessage("未能读取章节列表，未按完成处理");superStarLogService.saveOrUpdate(log);return;}
        int start=Math.max(0,Math.min(log.getCurrentChapterIndex()==null?0:log.getCurrentChapterIndex(),chapters.size()-1));
        int completed=start,firstIncomplete=-1;List<String> errors=new ArrayList<>();
        for(int index=start;index<chapters.size();index++){
            liveProgress.checkCancelled();ChapterPoint chapter=chapters.get(index);boolean chapterOk=true;
            liveProgress.chapter(index+1,chapters.size(),chapter.getTitle());
            try {
                Pair<List<Job>,List<JobInfo>> result=taskCourseUtil().getJobList(readyCourse.getClazzId(),readyCourse.getCourseId(),readyCourse.getCpi(),chapter.getId());
                List<Job> jobs=result.getFirst();List<JobInfo> infos=result.getSecond();
                if(infos!=null&&infos.stream().anyMatch(info->info!=null&&Boolean.TRUE.equals(info.getNotOpen())))throw new java.io.IOException("章节未开放");
                if(jobs==null)throw new java.io.IOException("任务列表读取失败");
                for(int j=0;j<jobs.size();j++){
                    liveProgress.checkCancelled();Job job=jobs.get(j);liveProgress.job(j+1,jobs.size(),job.getTitle());
                    try {
                        JobInfo context=job.getCardInfo()!=null?job.getCardInfo():(infos!=null&&j<infos.size()?infos.get(j):null);
                        if(context==null)throw new java.io.IOException("任务页参数缺失");
                        executeJob(readyCourse,job,context,log);
                    } catch(Exception e){liveProgress.checkCancelled();chapterOk=false;errors.add("第"+(index+1)+"章 "+job.getTitle()+"："+safeTaskError(e));liveProgress.failed("该任务点未完成，继续检查其余任务");}
                }
            } catch(Exception e){liveProgress.checkCancelled();chapterOk=false;errors.add("第"+(index+1)+"章："+safeTaskError(e));}
            if(chapterOk)completed++;else if(firstIncomplete<0)firstIncomplete=index;
            log.setCurrentChapterIndex(firstIncomplete<0?index:firstIncomplete);
            log.setCurrentProgress(BigDecimal.valueOf(completed*100.0/chapters.size()));
            log.setStatus(errors.isEmpty()?1:5);log.setErrorMessage(errors.isEmpty()?null:chapterErrorSummary(errors));log.setEndTime(LocalDateTime.now());superStarLogService.saveOrUpdate(log);
        }
        if(firstIncomplete<0){log.setStatus(4);log.setCurrentProgress(BigDecimal.valueOf(100));log.setErrorMessage(null);liveProgress.completed();}
        else{log.setStatus(5);log.setCurrentChapterIndex(firstIncomplete);liveProgress.failed("仍有未完成任务，请查看错误信息或重新检查");}
        log.setEndTime(LocalDateTime.now());superStarLogService.saveOrUpdate(log);
    }
    private String chapterErrorSummary(List<String> errors){String text=String.join("；",errors);return text.length()<=230?text:text.substring(0,230)+"…未完成共"+errors.size()+"项";}
    private String safeTaskError(Exception e){
        if(e instanceof java.io.IOException){String m=e.getMessage();return m==null?"网络或接口处理失败":m.substring(0,Math.min(140,m.length()));}
        return "任务处理失败（"+e.getClass().getSimpleName()+"）";
    }
    void executeJob(Course course,Job job,JobInfo context,SuperStarLog log) throws Exception {
        switch(job.getType()){
            case "video":taskCourseUtil().studyVideo(course,job,context,studyProperties.getSpeed(),"Video",log);break;
            case "audio":taskCourseUtil().studyVideo(course,job,context,studyProperties.getSpeed(),"Audio",log);break;
            case "document":taskCourseUtil().studyDocument(course,job);break;
            case "read":taskCourseUtil().studyRead(course,job,context,log);break;
            case "workid":taskCourseUtil().studyWork(course,job,context,log);break;
            default:throw new java.io.IOException("暂不支持该任务类型");
        }
    }

}

