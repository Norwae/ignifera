# Ignifera

Ignifera is an exporter of [akka-http](http://akka.io) statistics for [Prometheus](https://prometheus.io/). The 
exporter exposes either an endpoint of its own, or can be integrated into the routing directive.
  
## Features

Ignifera can be inserted into an existing application very simply: 

    import com.github.norwae.ignifera.{StatsCollector, StandaloneExport}
    
    val myRoutes: Route = ???
    
    Http().bindAndHandle(StatsCollector(myRoutes), "localhost", 8080) foreach { _ =>
      new StandaloneExport(8081).start()
    }
   
Altenatively, the metrics route can be included in another routing as follows (Be aware that this will slightly
modify statics as the metrics requests will be counted in the metrics themselves): 
    
    val myRoutes: Route = ???
    val fullRoutes = myRoutes ~ get(path("metrics")(IncludedHttpExport.statusRoute))
    Http().bindAndHandle(StatsCollector(fullRoutes), "localhost", 8080)
       

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
