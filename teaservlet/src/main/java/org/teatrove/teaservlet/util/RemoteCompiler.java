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

package org.teatrove.teaservlet.util;

import org.teatrove.tea.compiler.CompilationUnit;
import org.teatrove.tea.compiler.Compiler;
import org.teatrove.tea.compiler.TemplateRepository;
import org.teatrove.tea.util.AbstractFileCompiler;
import org.teatrove.trove.io.DualOutput;
import org.teatrove.trove.net.HttpClient;
import org.teatrove.trove.net.PlainSocketFactory;
import org.teatrove.trove.net.PooledSocketFactory;
import org.teatrove.trove.net.SocketFactory;
import org.teatrove.trove.util.ClassInjector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * RemoteCompiler compiles tea source files by reading them from a remote 
 * location specified by a URL. The compiled code can be written as class files
 * to a given destination directory, they can be passed to a ClassInjector, or
 * they can be sent to both.
 *
 * <p>When given a URL, RemoteCompiler compiles all files with the
 * extension ".tea". If a destination directory is used, tea files that have a
 * matching class file that is more up-to-date will not be compiled, unless
 * they are forced to be re-compiled.
 *
 * @author Jonathan Colwell
 * @version
 * <!--$$Revision:--> 16 <!-- $--> 21 <!-- $$JustDate:-->  3/19/04 <!-- $-->
 * @see ClassInjector
 */
public class RemoteCompiler extends AbstractFileCompiler {
    
    public static final String TEMPLATE_LOAD_PROTOCOL = "http://";
    private String[] mRemoteSourceDirs;
    private String mRootPackage;
    private File mRootDestDir;
    private ClassInjector mInjector;
    private String mEncoding;
    private boolean mForce = false;
    private Map mTemplateMap;
    private Map mSocketFactories;
    private long mTimeout;
    private long mPrecompiledTolerance;

    public RemoteCompiler(String[] rootSourceDirs,
                          String rootPackage,
                          File rootDestDir,
                          ClassInjector injector,
                          String encoding,
                          long timeout,
                          long precompiledTolerance) {
                          	
        super();
        mRemoteSourceDirs = rootSourceDirs;
        mRootPackage = rootPackage;
        mRootDestDir = rootDestDir;
        mInjector = injector;
        mEncoding = encoding;
        mTimeout = timeout;
        mPrecompiledTolerance = precompiledTolerance;

        if (mRootDestDir != null && 
            !mRootDestDir.isDirectory()) {
            throw new IllegalArgumentException
                ("Destination is not a directory: " + rootDestDir);
        }
        mSocketFactories = new HashMap();
        mTemplateMap = retrieveTemplateMap();

        if (! TemplateRepository.isInitialized())
            TemplateRepository.init(rootDestDir, rootPackage);
    }

    /**
     * @param force When true, compile all source, even if up-to-date
     */
    public void setForceCompile(boolean force) {
        mForce = force;
    }       
    
    /**
     * Checks that the source code for a specified template exists.
     */
    public boolean sourceExists(String name) {
        return mTemplateMap.containsKey(name);
    }
    
    public String[] getAllTemplateNames() {
        return (String[])mTemplateMap.keySet().toArray(new String[0]);
    }

    /**
     * Overrides the method from Compiler to allow timestamp synchronization
     * after the actual compilation has taken place.
     */
    public String[] compile(String[] names) throws IOException {
        String[] compiled = super.compile(names);
        if (getErrorCount() == 0) {
            for (int j=0;j<compiled.length;j++) {
                syncSources(compiled[j]);
            }
        }
        return compiled;
    }
    
    protected CompilationUnit createCompilationUnit(String name) {
        return new Unit(name,this);
    }

    /**
     * sets the template classes to have the same timestamp as the sources
     * to account for differences between the machine clocks.
     */
    private void syncSources(String name) {
        TemplateSourceInfo info = (TemplateSourceInfo)mTemplateMap.get(name);
        Unit compUnit = (Unit)this.getCompilationUnit(name,null);
        File destFile = compUnit.getDestinationFile();
        if (destFile != null) {
            destFile.setLastModified(info.timestamp);
        }
    }
      
    /**
     * returns a socket connected to a host running the TemplateServerServlet
     */
    HttpClient getTemplateServerClient(String remoteSource) throws IOException {
        SocketFactory factory = (SocketFactory)mSocketFactories.get(remoteSource);

        if (factory == null) {
            int port = 80;
            String host = remoteSource.substring(TEMPLATE_LOAD_PROTOCOL.length());

            int portIndex = host.indexOf("/");
        
            if (portIndex >= 0) {
                host = host.substring(0,portIndex);                         
            }
            portIndex = host.indexOf(":");
            if (portIndex >= 0) {
                try {
                    port = Integer.parseInt(host.substring(portIndex+1));
                }
                catch (NumberFormatException nfe) {
                    System.out.println("Invalid port number specified");
                }
                host = host.substring(0,portIndex);
            }
            factory = new PooledSocketFactory
                (new PlainSocketFactory(InetAddress.getByName(host), port, mTimeout));
            
            mSocketFactories.put(remoteSource, factory);
        }
        return new HttpClient(factory);
    }
    
    /**
     * turns a template name and a servlet path into a  
     */
    String createTemplateServerRequest(String servletPath,String templateName) {
        String pathInfo = servletPath.substring(servletPath.indexOf("/",TEMPLATE_LOAD_PROTOCOL.length()));
        if (templateName != null) {            
            pathInfo = pathInfo + templateName;
        }
        return pathInfo;
    }
    
    /**
     * creates a map relating the templates found on the template server
     * to the timestamp on the sourcecode
     */
    private Map retrieveTemplateMap() {
        Map templateMap = new TreeMap();
        for (int j=(mRemoteSourceDirs.length-1); j >= 0; j--) {
            String remoteSource = mRemoteSourceDirs[j];
            if (!remoteSource.endsWith("/")) {
                remoteSource = remoteSource + "/";
            }
            
            try {
                HttpClient tsClient = getTemplateServerClient(remoteSource);

                HttpClient.Response response = tsClient.setURI(createTemplateServerRequest(remoteSource,null))
                    .setPersistent(true).getResponse(); 

                if (response != null && response.getStatusCode() == 200) {

                    Reader rin = new InputStreamReader
                        (new BufferedInputStream(response.getInputStream()));
                
                    StreamTokenizer st = new StreamTokenizer(rin);
                    st.resetSyntax();
                    st.wordChars('!','{');
                    st.wordChars('}','}');
                    st.whitespaceChars(0,' ');
                    st.parseNumbers();
                    st.quoteChar('|');
                    st.eolIsSignificant(true);
                    String templateName = null; 
                    int tokenID = 0;
                    // ditching the headers by looking for "\r\n\r\n"
                    /* 
                     * no longer needed now that HttpClient is being used but leave
                     * in for the moment.
                     *
                     * while (!((tokenID = st.nextToken()) == StreamTokenizer.TT_EOL 
                     *       && st.nextToken() == StreamTokenizer.TT_EOL) 
                     *       && tokenID != StreamTokenizer.TT_EOF) {
                     * }
                     */
                    while ((tokenID = st.nextToken()) != StreamTokenizer.TT_EOF) {
                        if (tokenID == '|' || tokenID == StreamTokenizer.TT_WORD) {
                        
                            templateName = st.sval;
                        }
                        else if (tokenID == StreamTokenizer.TT_NUMBER 
                                 && templateName != null) {
                            templateName = templateName.substring(1);
                            //System.out.println(templateName);
                            templateMap.put(templateName.replace('/','.'),
                                            new TemplateSourceInfo(
                                                                   templateName,
                                                                   remoteSource,
                                                                   (long)st.nval));
                            templateName = null;
                        }
                    }
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        //System.out.println("retrieving templateMap");
        return templateMap;
    }
    
    /**
     * inner class to store a templates' name, server location and timestamp
     */
    private class TemplateSourceInfo {
        public String name;
        public String server;
        public long timestamp;
        
        TemplateSourceInfo(String name,String server,long timestamp) {
            this.name = name;
            this.server = server;
            this.timestamp = timestamp;
        }
    }
    
    public class Unit extends CompilationUnit {


        private String mSourceFilePath;
        private String mDotPath;
        private File mDestDir;
        private File mDestFile;
        private String mSourceUrl;
        
        Unit(String name, Compiler compiler) {
            super(name,compiler);
            mDotPath = name;
            String slashPath = name.replace('.','/');
            if (slashPath.endsWith("/")) {
                slashPath = slashPath.substring(0, slashPath.length() - 1);
            }
            if (mRootDestDir != null) {
                if (slashPath.lastIndexOf('/') >= 0) {
                    mDestDir = new File
                        (mRootDestDir,
                         slashPath.substring(0,slashPath.lastIndexOf('/')));
                }
                else {
                    mDestDir = mRootDestDir;
                }
                mDestDir.mkdirs();          
                mDestFile = new File
                    (mDestDir,
                     slashPath.substring(slashPath.lastIndexOf('/') + 1) 
                     + ".class");
            /*
            try {
                if (mDestFile.createNewFile()) {
                    System.out.println(mDestFile.getPath() + " created");
                }
                else {
                    System.out.println(mDestFile.getPath() + " NOT created");
                }
            }
            catch (IOException ioe) {ioe.printStackTrace();}
            */
            }
            mSourceFilePath = slashPath;
            
            TemplateSourceInfo info = (TemplateSourceInfo) mTemplateMap.get(mDotPath);
            if(info==null) {
                mSourceUrl = getSourceFileName();
            } else if(info.server.trim().endsWith("/")) {
                mSourceUrl = info.server.trim() + getSourceFileName();
            } else {
                mSourceUrl = info.server.trim() + "/" + getSourceFileName();
            }
        }

        public String getTargetPackage() {
            return mRootPackage;
        }

        public String getSourceFileName() {
            return mSourceFilePath + ".tea";
        }

        /**
         * comparable to the getSourceFile() method of FileCompiler
         */
        public String getSourceUrl() {
            return mSourceUrl;
        }

        public Reader getReader() throws IOException {
            Reader reader = null;
            InputStream in = getTemplateSource(mDotPath);
            if (mEncoding == null) {
                reader = new InputStreamReader(in);
            }
            else {
                reader = new InputStreamReader(in, mEncoding);
            }
            return reader;
        }

        public boolean shouldCompile() {
            long remoteTimeStamp = ((TemplateSourceInfo) mTemplateMap.get(mDotPath)).timestamp;
            final TemplateRepository.TemplateInfo templateInfo = TemplateRepository.getInstance()
                    .getTemplateInfo(mDotPath);

            boolean isWindows = System.getProperty("os.name").startsWith("Windows");
            if (!mForce &&
                mDestFile != null &&
                mDestFile.exists() && 
                ((isWindows && mDestFile.lastModified() >= remoteTimeStamp) ||
                (!isWindows && Math.max(mDestFile.lastModified(), remoteTimeStamp) - 
                    Math.min(mDestFile.lastModified(), remoteTimeStamp) < 1000L))) {

                return false;
            } else
            // Try to defer to the template repository (and possibly precompiled templates)
            if(
                    !mForce &&
                        (mDestFile == null || !mDestFile.exists()) &&
                        templateInfo != null &&
                        gteq(templateInfo.getLastModified(), remoteTimeStamp, mPrecompiledTolerance))
            {
                return false;
            }


            return true;
        }

        /**
         * Returns the truth that a is greater than or equal to b assuming the given tolerance.
         * <p>
         * ex. tolerance = 10, a = 1000 and b = 2000, returns false<br/>
         * ex. tolerance = 10, a = 1989 and b = 2000, returns false<br/>
         * ex. tolerance = 10, a = 1990 and b = 2000, returns true<br/>
         * ex. tolerance = 10, a = 2000 and b = 2000, returns true<br/>
         * ex. tolerance = 10, a = 3000 and b = 2000, returns true<br/>
         * </p>
         *
         * @param a
         * @param b
         * @param tolerance
         * @return
         */
        private boolean gteq(long a, long b, long tolerance) {
            return a >= b - tolerance;
        }


        /**
         * @return the file that gets written by the compiler.
         */
        public File getDestinationFile() {
            return mDestFile;
        }

        public OutputStream getOutputStream() throws IOException {
            OutputStream out1 = null;
            OutputStream out2 = null;

            if (mDestDir != null) {
                if (!mDestDir.exists()) {
                    mDestDir.mkdirs();
                }

                out1 = new FileOutputStream(mDestFile);
            }

            if (mInjector != null) {
                String className = getName();
                String pack = getTargetPackage();
                if (pack != null && pack.length() > 0) {
                    className = pack + '.' + className;
                }
                out2 = mInjector.getStream(className);
            }

            OutputStream out;

            if (out1 != null) {
                if (out2 != null) {
                    out = new DualOutput(out1, out2);
                }
                else {
                    out = out1;
                }
            }
            else if (out2 != null) {
                out = out2;
            }
            else {
                out = new OutputStream() {
                    public void write(int b) {}
                    public void write(byte[] b, int off, int len) {}
                };
            }

            return new BufferedOutputStream(out);
        }
        
        /**
         * get a input stream containing the template source data.
         */
        private InputStream getTemplateSource(String templateSourceName) 
                                                        throws IOException {
            TemplateSourceInfo tsInfo = (TemplateSourceInfo)mTemplateMap
                                                .get(templateSourceName);

            HttpClient client = getTemplateServerClient(tsInfo.server);
            HttpClient.Response response = client
                .setURI(createTemplateServerRequest(tsInfo.server,tsInfo.name + ".tea"))
                .setPersistent(true).getResponse();            
            InputStream in = response.getInputStream();
            return in;
        }
    }
}
