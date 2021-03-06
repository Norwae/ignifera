# Ignifera

1) [Prometheus](https://prometheus.io/) statistics export and collection for
akka http routes. The library collects http result codes,
timings, and requests in flight. It additionally optionally
exposes some basic akka statistics.

2) [Graceful shutdown](https://blog.risingstack.com/graceful-shutdown-node-js-kubernetes/), health and readiness 
functions. These routes are provided at a low level to they can be used by both the routing
DSL and special case implementations.

3) Access log collection.

These functions can be freely composed to (e.g.) exclude health check
routes from statistics and access log, or include them, depending
on the requirements and standards of the user

  
## Features

Ignifera can be inserted into an existing application very simply: 

    import com.github.norwae.ignifera.{StatsCollector, StandaloneExport}
    
    val myRoutes: Route = ???
    
    Http().bindAndHandle(GracefulShutdownSupport(StatsCollector(myRoutes)), "localhost", 8080) foreach { _ =>
      new StandaloneExport(8081).start()
    }
   
Alternatively, the metrics route can be included in another routing as follows (Be aware that this will slightly
modify statics as the metrics requests will be counted in the metrics themselves): 
    
    val myRoutes: Route = ???
    val fullRoutes = myRoutes ~ get(path("metrics")(IncludedHttpExport.statusRoute))
    Http().bindAndHandle(StatsCollector(fullRoutes), "localhost", 8080)
       
As you can decide to include both metrics, support for the graceful shutdown or either one by itself. If you include
the `StatsCollector` inside the `GracefulShutdownSupport`, health checks will not be metered. By reversing the 
order, they will be metered. 

If the `GracefulShutdownSupport` has been included it can be configured to send a final metrics push to a specified 
endpoint before shutting down. To activate this feature the environment variable `PROMETHEUS_PUSH_GATEWAY` has to be set
 to the target which should receive the metrics.

## Exported Metrics

### http_requests_in_flight 
Gauge - Requests currently in flight. Will respond to the number of requests currently en-route between the start and 
completion of routing. This will not necessarily signal completion of response streaming, only that the response 
status code and headers have been decided.

### http_requests_total
Count  - Requests processed by the application. As above, does not require complete transmission of response data 
(or even request body, if it is ignored).  

### http_request_duration_microseconds
Summary - Time to response determined. The response time is counted from beginning of routing, and internally has the 
same resolution as the java method `System.nanotime()` 

#### Dimensions
* method - Http method of the requests
* code - Response status code

### http_response_size_bytes
Summary - Response size (estimated). Bytes to be streamed to the client. This includes entity size, and any headers set 
by the routing layer. Depending on the location the stats collection is included, it may not necessarily include headers 
added by content negotiation. Detection depends on the presence of a `Content-Length` header. Responses lacking this 
header will not be included in the collected statistics.

### http_request_size_bytes
Summary - Request size (estimated). Bytes bytes submitted by the client. This includes entity size, and any headers set 
by the client (including cookies). Detection depends on the presence of a `Content-Length` header. Responses lacking 
this  header will not be included in the collected statistics.
