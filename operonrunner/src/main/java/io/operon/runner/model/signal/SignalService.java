/** OPERON-LICENSE **/
package io.operon.runner.model.signal;

import java.util.Date;

import java.util.concurrent.Callable;
import io.operon.runner.OperonContext;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.signal.*;

// 
// Currently this runs only HeartBeat
// 
public class SignalService implements Callable<Void> {

	//private Signal signal;
	private long duration;
	private OperonContext ctx;

    public SignalService(OperonContext ctx, long duration) {
		this.ctx = ctx;
		this.duration = duration;
    }

    @Override
    public Void call() throws OperonGenericException {
        Date now = new Date();
        //HeartBeatSignal hbs = new HeartBeatSignal(now.getTime());
        try {
            while(ctx.isShutdown() == false) {
	            Thread.sleep(this.duration);
	            this.ctx.heartBeatAction();
            }
        } catch (java.lang.InterruptedException ie) {
            // noop
            System.out.println("Signal interrupted.");
        } catch (Exception e) {
            System.err.println("ERROR: AT: " + e.getMessage());
        }
        return null; // because return type is Void
    }

}