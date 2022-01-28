/** OPERON-LICENSE **/
package io.operon.runner.model.signal;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.Path;

//
// 
//
public interface Signal {
	
	public void setTimestamp(long ts);
	
	public long getTimestamp();
	
	public String toString();
	
}