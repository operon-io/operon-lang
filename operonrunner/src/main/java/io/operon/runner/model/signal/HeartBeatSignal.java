/** OPERON-LICENSE **/
package io.operon.runner.model.signal;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.Path;

//
// Models HeartBeat-signal
//
public class HeartBeatSignal implements Signal {

    private long timestamp;
    
    public HeartBeatSignal() {}
    
    public HeartBeatSignal(long ts) {
    	this.setTimestamp(ts);
    }

	public void setTimestamp(long ts) {
		this.timestamp = ts;
	}
	
	public long getTimestamp() {
		return this.timestamp;
	}

	public String toString() {
		return "HeartBeat(" + String.valueOf(this.getTimestamp()) + ")";
	}
}