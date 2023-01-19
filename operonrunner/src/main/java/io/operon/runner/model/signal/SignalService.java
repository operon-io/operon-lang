/*
 *   Copyright 2022-2023, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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