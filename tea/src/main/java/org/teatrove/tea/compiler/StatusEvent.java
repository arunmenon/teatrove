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

package org.teatrove.tea.compiler;

/**
 * 
 * @author Sean T. Treat
 * @version
 * <!--$$Revision:--> 1 <!-- $-->, <!--$$JustDate:-->  5/14/01 <!-- $-->
 */
public class StatusEvent extends java.util.EventObject {

    private int mTotal;
    private int mCurrent;
    private String mCurrentName;
    
    public StatusEvent(Object source, int current, int total, 
                       String currentName) {        
        super(source);
        mTotal = total;
        mCurrent = current;
        mCurrentName = currentName;
    }

    /**
     * Returns the total number of files that are being worked.
     */
    public int getTotal() {
        return mTotal;
    }

    /**
     * Returns the index of the current file.
     */
    public int getCurrent() {
        return mCurrent;
    }

    /**
     * Returns the name of the file that is being compiled.
     */
    public String getCurrentName() {
        return mCurrentName;
    }
}
