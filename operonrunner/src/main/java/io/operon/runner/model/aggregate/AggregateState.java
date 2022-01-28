/** OPERON-LICENSE **/
package io.operon.runner.model.aggregate;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;

import io.operon.runner.node.Node;
import io.operon.runner.node.Aggregate;
import io.operon.runner.node.type.OperonValue;

public class AggregateState {

    private String id;
    private Aggregate aggregateNode;
    private Long timeoutStart; // timestamp: milliseconds
    private Long timeoutDuration; // milliseconds
    private transient ExecutorService timeoutExecutorService; // cannot serialize this service.

    // @ String = correlationKey
    // @ OperonValue = the aggregated value (ArrayType if no aggregationFunction was given)
    private Map<String, Node> result;

    public AggregateState(Aggregate aggregate) {
        this.aggregateNode = aggregate;
        this.timeoutExecutorService = Executors.newFixedThreadPool(3); // Executors.newSingleThreadExecutor();
        this.result = Collections.synchronizedMap(new HashMap<String, Node>());
    }

    public Aggregate getAggregate() {
        return this.aggregateNode;
    }

    public void setId(String i) {
        this.id = i;
    }
    
    public String getId() {
        return this.id;
    }

    public Long getTimeoutStart() {
        return this.timeoutStart;
    }
    
    public void setTimeoutStart(Long ts) {
        this.timeoutStart = ts;
    }

    public Long getTimeoutDuration() {
        return this.timeoutDuration;
    }
    
    public void setTimeoutDuration(Long d) {
        this.timeoutDuration = d;
    }

    public synchronized Map<String, Node> getResult() {
        return this.result;
    }

}