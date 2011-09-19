/*
 *  Copyright 1997-2011 teatrove.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.teatrove.barista.admin;

import javax.servlet.*;
import org.teatrove.trove.util.tq.TransactionQueueData;
import org.teatrove.trove.util.tq.TransactionQueue;

/**
 * @author Brian S O'Neill
 */
public interface BaristaAdminContext {
    public String decodeStatusCode(int sc);

    public TransactionQueueData[] getTransactionQueueData(String handlerName,
                                                          String stageName,
                                                          String servletName,
                                                          String queueName)
        throws ServletException;

    public void resetTransactionQueues(String handlerName,
                                       String stageName,
                                       String servletName,
                                       String queueName)
        throws ServletException;

    public BaristaAdmin getBaristaAdmin() throws ServletException;

    public TransactionQueue getTransactionQueue(String queueName) throws ServletException;
}
