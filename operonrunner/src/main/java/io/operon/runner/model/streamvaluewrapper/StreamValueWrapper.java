/*
 *   Copyright 2022, operon.io
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

package io.operon.runner.model.streamvaluewrapper;

import java.io.InputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.CharBuffer;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;

public interface StreamValueWrapper extends Closeable {

    public boolean supportsJson();
    
    public void setSupportsJson(boolean sj);

    public void close() throws IOException;
    
    public OperonValue readJson() throws OperonGenericException;

}