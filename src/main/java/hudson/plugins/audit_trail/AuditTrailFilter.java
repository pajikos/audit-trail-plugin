/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.audit_trail;

import com.google.inject.Injector;
import hudson.Extension;
import hudson.init.Initializer;
import hudson.model.User;
import hudson.util.PluginServletFilter;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;

/**
 * Servlet filter to watch requests and log those we are interested in.
 * @author Alan Harder
 * @author Pierre Beitz
 */
@Extension
public class AuditTrailFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(AuditTrailFilter.class.getName());

    private static Pattern uriPattern = null;

    @Inject
    private AuditTrailPlugin configuration;

    /**
     * @deprecated as of 2.6
     **/
    @Deprecated
    public AuditTrailFilter(AuditTrailPlugin plugin) {
        this.configuration = plugin;
    }

    public AuditTrailFilter() {
        // used by the injector
    }

    public void init(FilterConfig fc) {
    }

    static void setPattern(String pattern) throws PatternSyntaxException {
        uriPattern = Pattern.compile(pattern);
        LOGGER.log(Level.FINE, "set pattern to {0}", pattern);
    }

    public void doFilter(ServletRequest request, ServletResponse res, FilterChain chain)
          throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = getPathInfo(req);
        if (uriPattern != null && uriPattern.matcher(uri).matches()) {
            User user = User.current();
            String username = user != null ? user.getId() : req.getRemoteAddr();
            String extra = "";
            // For queue items, show what task is in the queue:
            if (uri.startsWith("/queue/item/")) {
                extra = extractInfoFromQueueItem(uri);
            } else if (uri.startsWith("/queue/cancelItem")) {
                extra = getFormattedQueueItemUrlFromItemId(Integer.parseInt(req.getParameter("id")));
                // not sure of the intent of the original author
                // it looks to me we should always log the query parameters
                // could we leak sensitive data?  There shouldn't be any in a query parameter...except for a badly coded plugin
                // let's see if this becomes a wanted feature...
                uri += "?" + req.getQueryString();
            } else if (uri.contains("/createItem")) {
                extra = formatExtraInfoString(req.getParameter("name"));
            }

            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE, "Audit request {0} by user {1}", new Object[]{uri, username});

            onRequest(uri, extra, username);
        } else {
            LOGGER.log(Level.FINEST, "Skip audit for request {0}", uri);
        }
        chain.doFilter(req, res);
    }

    private String extractInfoFromQueueItem(String uri) {
        try {
            int itemId = Integer.parseInt(uri.substring(12, uri.indexOf('/', 13)));
            return getFormattedQueueItemUrlFromItemId(itemId);
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Error occurred while parsing queue item", e);
        }
        return "";
    }

    private String getFormattedQueueItemUrlFromItemId(int itemId) {
        return formatExtraInfoString(Jenkins.getInstance().getQueue().getItem(itemId).task.getUrl());
    }

    private String formatExtraInfoString(String toFormat) {
        return String.format(" (%s)", toFormat);
    }

    public void destroy() {
    }

    // the default milestone doesn't seem right, as the injector is not available yet (at least with the JenkinsRule)
    @Initializer(after = EXTENSIONS_AUGMENTED)
    public static void init() throws ServletException {
        Injector injector = Jenkins.getInstance().getInjector();
        if (injector == null) {
            return;
        }
        PluginServletFilter.addFilter(injector.getInstance(AuditTrailFilter.class));
    }

    private void onRequest(String uri, String extra, String username) {
        if (configuration != null) {
            for (AuditLogger logger : configuration.getLoggers()) {
                logger.log(uri + extra + " by " + username);
            }
        }
    }

    // See SECURITY-1815
    private static String getPathInfo(HttpServletRequest request) {
        return canonicalPath(request.getRequestURI().substring(request.getContextPath().length()));
    }

    // Copied from Stapler#canonicalPath
    private static String canonicalPath(String path) {
        List<String> r = new ArrayList<>(Arrays.asList(path.split("/+")));
        for (int i = 0; i < r.size(); ) {
            if (r.get(i).length() == 0 || r.get(i).equals(".")) {
                // empty token occurs for example, "".split("/+") is [""]
                r.remove(i);
            } else if (r.get(i).equals("..")) {
                // i==0 means this is a broken URI.
                r.remove(i);
                if (i > 0) {
                    r.remove(i - 1);
                    i--;
                }
            } else {
                i++;
            }
        }

        StringBuilder buf = new StringBuilder();
        if (path.startsWith("/")) {
            buf.append('/');
        }
        boolean first = true;
        for (String token : r) {
            if (!first) buf.append('/');
            else first = false;
            buf.append(token);
        }
        // translation: if (path.endsWith("/") && !buf.endsWith("/"))
        if (path.endsWith("/") && (buf.length() == 0 || buf.charAt(buf.length() - 1) != '/')) {
            buf.append('/');
        }
        return buf.toString();
    }
}
