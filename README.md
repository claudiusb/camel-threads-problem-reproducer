# Camel Threads Problem Reproducer

# What does this reproducer show?

A route is created that is supposed to handle heavy computation asynchronously. The route is invoked synchronously but
should turn into async in order to return immediately and process the heavy computation with a thread pool. If all threads
of that pool are busy and the queue is full, the pool should reject new tasks by throwing an exception.
The scenario here is, that new messages are sent to the route concurrently faster than the routes thread pool can handle new tasks.

See com.github.claudiusb.camelthreadsproblemreproducer.ThreadsProblemReproducer under src/test.

# Expectation:

The routes thread pool size increases until it reaches its max of 10 and the queue is full after the first
~30 tasks. After that, sending new messages to the route should be rejected with a org.apache.camel.CamelExchangeException caused
by a java.util.concurrent.RejectedExecutionException.

# Observation:

It seems like threads that submit exchanges to the route block until the routes thread pool can handle new tasks. No exceptions
are thrown from the template.

# Component

org.apache.camel.model.ProcessorDefinition#threads() methods.

# Version

Camel 2.14.0
