package org.ceskaexpedice.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Concurrency Utils
 * @author petr.podsednik
 */
public final class ConcurrencyUtils {
  private ConcurrencyUtils() {
  }

  /**
   * Runs one task in n threads. All threads run the task at the same time. The method is waiting for all threads to complete.
   * @param nThreads number of threads
   * @param task the task to run
   * @return processing time
   */
  public static long runTask(int nThreads, final Runnable task) {
    final CountDownLatch startGate = new CountDownLatch(1);
    final CountDownLatch endGate = new CountDownLatch(nThreads);
    for (int i = 0; i < nThreads; i++) {
      Thread t = new Thread(() -> {
        try {
          startGate.await();
          try {
            task.run();
          } finally {
            endGate.countDown();
          }
        } catch (InterruptedException ignored) {
          // ignore
        }
      });
      t.start();
    }
    long start = System.nanoTime();
    startGate.countDown();
    try {
      endGate.await();
    } catch (InterruptedException ignore) {
      // ignore
    }
    long end = System.nanoTime();
    return end - start;
  }

  /**
   * Runs factory tasks in n threads - each factory task with one thread. The method waits for all threads to finish.
   * @param nThreads number of threads
   * @param taskFactory the function used to provide tasks
   * @return processing time
   */
  public static long runFactoryTasks(int nThreads, Function<Integer, TestTask> taskFactory) {
    final List<TestTask> testTasks = createTasks(nThreads, taskFactory);
    long start = System.nanoTime();
    executeTasks(testTasks);
    long end = System.nanoTime();
    return end - start;
  }

  private static List<TestTask> createTasks(int nTasks, Function<Integer, TestTask> taskFactory) {
    List<TestTask> testTasks = new ArrayList<>();
    for (int i = 0; i < nTasks; i++) {
      TestTask testTask = taskFactory.apply(i);
      testTasks.add(testTask);
    }
    return testTasks;
  }

  private static void executeTasks(List<TestTask> testTasks) {
    ExecutorService executorService = Executors.newFixedThreadPool(testTasks.size());
    for (TestTask testTask : testTasks) {
      executorService.execute(testTask);
    }
    executorService.shutdown();
    while (!executorService.isTerminated()) ;
  }

  public static abstract class TestTask implements Runnable {
    protected String taskId;

    protected TestTask(String taskId) {
      this.taskId = taskId;
    }

    @Override
    public void run() {
      setName();
    }

    private void setName() {
      Thread.currentThread().setName("TestThread-" + taskId);
    }

    @Override
    public String toString() {
      return this.getClass().getSimpleName() + "{" + "taskId='" + taskId + '\'' + '}';
    }

  }

}
