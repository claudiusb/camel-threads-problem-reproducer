package com.github.claudiusb.camelthreadsproblemreproducer;

import com.google.common.base.Stopwatch;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Reproduces the problem, see single @Test method.
 */
public class ThreadsProblemReproducer extends CamelTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadsProblemReproducer.class);

    @Produce(uri = "direct:myRoute")
    protected ProducerTemplate template;

    ExecutorService exec;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        exec = Executors.newFixedThreadPool(100);
    }

    /**
     * What does this test do?
     * A route is created that is supposed to handle heavy computation asynchronously. The route is invoked synchronously but
     * should turn into async in order to return immediately and process the heavy computation with a thread pool. If all threads
     * of that pool are busy and the queue is full, the pool should reject new tasks by throwing an exception.
     * The scenario here is, that new messages are sent to the route concurrently faster than the routes thread pool can handle new tasks.
     * <p/>
     * Expectation:
     * The routes thread pool size increases until it reaches its max of 10 and the queue is full after the first
     * ~30 tasks. After that, sending new messages to the route should be rejected with a org.apache.camel.CamelExchangeException caused
     * by a java.util.concurrent.RejectedExecutionException. In both cases (the routes thread pool can / cannot handle more tasks) the
     * route should not block the calling thread.
     * <p/>
     * Observation:
     * In the beginning the route accepts / rejects tasks as expected. An invocation of the route takes about 20-40ms.
     * At some points invocations of the route take considerably longer (around 6s). It seems like threads that
     * submit exchanges to the route block until the routes thread pool can handle new tasks.
     * <p/>
     * Component
     * org.apache.camel.model.ProcessorDefinition#threads() methods.
     * <p/>
     * Version
     * Camel 2.14.0
     */
    @Test
    public void reproduceProblem() throws Exception {
        int tasksCount = 10000;
        for (int i = 0; i < tasksCount; i++) {
            exec.submit(new InvokeRouteTask(i));
        }

        Thread.sleep(20 * 1000L);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:myRoute")
                        .threads(5, 10, "myRouteThreadPool")
                        .maxQueueSize(20)
                                // It tasks are busy and queue is full,
                                // new tasks should be rejected with an exception.
                        .rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .callerRunsWhenRejected(false)
                        .log(LoggingLevel.INFO, LOGGER, "Processing ${in.body}")
                        .delay(2000);
            }
        };
    }

    class InvokeRouteTask implements Runnable {
        private int idx;

        public InvokeRouteTask(int idx) {
            this.idx = idx;
        }

        @Override
        public void run() {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            try {
                template.sendBody("Task #" + idx);
            } catch (CamelExecutionException e) {
                LOGGER.error(String.format("Task #%d: Caught %s: %s", idx, e.getClass().getName(), e.getMessage()));
            }
            stopwatch.stop();
            // At some point the execution takes longer and longer.
            LOGGER.info(String.format("Task #%d took %d millis to invoke the route", idx,
                    stopwatch.elapsed(TimeUnit.MILLISECONDS)));
        }
    }
}
