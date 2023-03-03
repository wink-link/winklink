package com.tron.job;

import com.beust.jcommander.internal.Sets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.tron.common.Constant;
import com.tron.job.adapters.AdapterManager;
import com.tron.job.adapters.BaseAdapter;
import com.tron.web.common.util.R;
import com.tron.web.entity.JobSpec;
import com.tron.web.entity.TaskSpec;
import com.tron.web.service.JobSpecsService;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobCache {

  @Autowired
  private JobSpecsService jobSpecsService;

  @Value("${node.cacheEnable:#{false}}")
  private Boolean cacheEnable;

  @Value("${node.cacheCount:#{5}}")
  private int cacheCount;

  private Set<String> jobList = Sets.newHashSet();
  private Cache<String, Queue<Long>> jobResultCache = CacheBuilder.newBuilder().maximumSize(10000)
      .expireAfterWrite(30, TimeUnit.MINUTES).build();

  private ScheduledExecutorService cacheExecutor = Executors.newSingleThreadScheduledExecutor();

  public void run() {
    if (cacheEnable) {
      init();
      cacheExecutor.scheduleWithFixedDelay(()->{
        try {
          System.out.println("test!!!");
          cache();
        } catch (Throwable e) {
          log.warn("cache schedule run error, error msg:" + e.getMessage());
        }
      }, 0, 1, TimeUnit.MINUTES);
    } else {
      System.out.println("cache schedule has been closed!");
      log.info("cache schedule has been closed!");
    }
  }

  private void init() {
    List<JobSpec> jobSpecs = jobSpecsService.getAllJob();
    for (JobSpec jobSpec : jobSpecs) {
      if (jobSpec.getDeletedAt() != null) {
        continue;
      }
      List<TaskSpec> taskSpecs = jobSpecsService.getTasksByJobId(jobSpec.getId());
      for (TaskSpec taskSpec : taskSpecs) {
        if (taskSpec.getType().equals(Constant.TASK_TYPE_CACHE)) {
          addToCacheList(jobSpec.getId());
        }
      }
    }
  }

  private void cache() {
    jobList.forEach(
        jobId->{
          try {
            R ret = getJobResultById(jobId);
            if (ret.get("code").equals(0)) {
              cachePut(jobId, (Long)ret.get("result"));
            }
          } catch (Exception e) {
            log.warn("cache job {} failed", jobId);
          }
        }
    );
  }

  public Boolean addToCacheList(String jobId) {
    try {
      jobList.add(jobId);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public void cachePut(String jobId, Long value) {
    Queue<Long> valueList = jobResultCache.getIfPresent(jobId);
    if (valueList == null) {
      valueList = new LinkedList<>();
      addToCacheList(jobId);
    }
    valueList.offer(value);

    while (valueList.size() > cacheCount) {
      valueList.poll();
    }
    jobResultCache.put(jobId, valueList);
  }

  public Long cacheGet(String jobId) {
    Queue<Long> valueList = jobResultCache.getIfPresent(jobId);
    if (valueList != null) {
        double average = valueList.stream().mapToLong(v->v).average().orElse(0.0);
      return Math.round(average);
    } else {
      return null;
    }
  }

  public R getJobResultById(String jobId) {
    JobSpec job = jobSpecsService.getById(jobId);

    R preTaskResult = new R();
    preTaskResult.put("result", null);
    for (TaskSpec taskSpec : job.getTaskSpecs()) {
      if (taskSpec.getType().equals(Constant.TASK_TYPE_TRON_TX) ||
          taskSpec.getType().equals(Constant.TASK_TYPE_CACHE)) {
        break;
      }
      log.info("{} Task executing: {}", jobId, taskSpec.getType());

      BaseAdapter adapter = AdapterManager.getAdapter(taskSpec);
      R r = adapter.perform(preTaskResult);
      log.info("{} Task executed: {} | Result: {}", jobId, taskSpec.getType(), r);
      if (r.get("code").equals(0)) {
        preTaskResult.replace("result", r.get("result"));
      } else {
        log.error(taskSpec.getType() + " run failed when get job result, job id: {}, msg: {}", jobId, r.get("msg"));
        preTaskResult.replace("code", r.get("code"));
        preTaskResult.replace("msg", r.get("msg"));
        break;
      }
    }

    return preTaskResult;
  }

  public Boolean isCacheEnable() {
    return cacheEnable;
  }

  public Queue<Long> getValueListByJobId(String jobId) {
    return jobResultCache.getIfPresent(jobId);
  }
}
