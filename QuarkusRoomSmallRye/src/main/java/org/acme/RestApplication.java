package org.acme;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("rest")
public class RestApplication extends Application {
    /*
     * The context root for this application is /
     * @ApplicationPath will tuck the entirety of the REST endpoint under /rest/
     * Any {@link Path} annotations at the class level are appended onto that, etc.
     */
}
