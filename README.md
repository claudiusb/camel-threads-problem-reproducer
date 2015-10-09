# Camel Threads Problem Reproducer

# What does this reproducer show?

A route is created that is supposed to handle heavy computation asynchronously. The route is invoked synchronously but
should turn into async in order to return immediately and process the heavy computation with a thread pool. If all threads
of that pool are busy and the queue is full, the pool should reject new tasks by throwing an exception.
The scenario here is, that new messages are sent to the route concurrently faster than the routes thread pool can handle new tasks.

See com.github.claudiusb.camelthreadsproblemreproducer.ThreadsProblemReproducer under src/test for details and further explanation.
