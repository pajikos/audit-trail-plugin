/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package hudson.plugins.audit_trail;


import com.cloudbees.workflow.rest.external.AtomFlowNodeExt;
import com.cloudbees.workflow.rest.external.RunExt;
import com.cloudbees.workflow.rest.external.StageNodeExt;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.audit_trail.utils.DatadogUtilities;
import hudson.plugins.audit_trail.utils.TagsUtil;

import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


/**
 * This class registers an {@link RunListener} to trigger events and calculate metrics:
 * - When a build initializes, the {@link #onInitialize(Run)} method will be invoked.
 * - When a build setup the environment, the {@link #setUpEnvironment(AbstractBuild, Launcher, BuildListener)} method will be invoked.
 * - When a build starts, the {@link #onStarted(Run, TaskListener)} method will be invoked.
 * - When a build completes, the {@link #onCompleted(Run, TaskListener)} method will be invoked.
 * - When a build finalizes, the {@link #onFinalized(Run)} method will be invoked.
 */
@Extension
public class DatadogBuildListener extends RunListener<Run> {

    private static final Logger logger = Logger.getLogger(DatadogBuildListener.class.getName());

    @Inject
    AuditTrailPlugin configuration;

    /**
     * Called when a build is first initialized.
     *
     * @param run - A Run object representing a particular execution of Job.
     */
    @Override
    public void onInitialize(Run run) {
        try {
            // Process only if job is NOT in excluded and is in included

            logger.fine("Start DatadogBuildListener#onInitialize");


            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, null);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }
            for (AuditLogger logger : configuration.getLoggers()) {
                logger.log("Audit onInitialize:");
                for (Map.Entry<String, Set<String>> entry : buildData.getTags().entrySet())
                    logger.log("Key = " + entry.getKey() +
                            ", Value = " + entry.getValue());
            }
            // Traces
            logger.fine("End DatadogBuildListener#onInitialize");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        }
    }


    /**
     * Called when a build is first started.
     *
     * @param run      - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     *                 operation.
     */
    @Override
    public void onStarted(Run run, TaskListener listener) {
        try {
            // Process only if job is NOT in excluded and is in included

            logger.fine("Start DatadogBuildListener#onStarted");

            // Get Datadog Client Instance


            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }


            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname("unknown");
            tags.put("hostname", Collections.singleton(hostname));
            for (AuditLogger logger : configuration.getLoggers()) {
                logger.log("Audit onStarted:");
                for (Map.Entry<String, Set<String>> entry : tags.entrySet())
                    logger.log("Key = " + entry.getKey() +
                            ", Value = " + entry.getValue());
            }
            logger.fine("End DatadogBuildListener#onStarted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        }
    }

    /**
     * Called when a build is completed.
     *
     * @param run      - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     *                 operation.
     */

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        try {
            logger.fine("Start DatadogBuildListener#onCompleted");
            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }

            // Send a metric
            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname("unknown");
            logger.fine(String.format("[%s]: Duration: %s", buildData.getJobName(null), toTimeString(buildData.getDuration(0L))));

            if (run instanceof WorkflowRun) {
                RunExt extRun = getRunExtForRun((WorkflowRun) run);
                long pauseDuration = 0;
                for (StageNodeExt stage : extRun.getStages()) {
                    Set stageFlowNodesSet = new HashSet<String>();
                    List<AtomFlowNodeExt> stageFlowNodes = stage.getStageFlowNodes();
                    for (AtomFlowNodeExt afne : stageFlowNodes) {
                        stageFlowNodesSet.add(afne.getName());
                        stageFlowNodesSet.add(afne.getExecNode());
                    }
                    tags = TagsUtil.merge(tags,
                            Collections.singletonMap("flow_nodes", stageFlowNodesSet));
                    pauseDuration += stage.getPauseDurationMillis();
                    tags.put("child_nodes", new HashSet<>(stage.getAllChildNodeIds()));
                    tags = TagsUtil.merge(tags,
                            Collections.singletonMap("exec_node", Collections.singleton(stage.getExecNode())));
                    tags = TagsUtil.merge(tags,
                            Collections.singletonMap("stage_node_name", Collections.singleton(stage.getName())));
                }
                tags.put("pauseDuration", Collections.singleton("" + pauseDuration));

            }
            tags.put("hostname", Collections.singleton(hostname));
            for (AuditLogger logger : configuration.getLoggers()) {
                logger.log("Audit onCompleted:");
                for (Map.Entry<String, Set<String>> entry : tags.entrySet())
                    logger.log("Key = " + entry.getKey() +
                            ", Value = " + entry.getValue());
            }

            logger.fine("End DatadogBuildListener#onCompleted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        }
    }


    /**
     * Called when a build is finalized.
     *
     * @param run - A Run object representing a particular execution of Job.
     */
    @Override
    public void onFinalized(Run run) {
        try {

            logger.fine("Start DatadogBuildListener#onFinalized");

            // Get Datadog Client Instance

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, null);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }
            for (AuditLogger logger : configuration.getLoggers()) {
                logger.log("Audit onFinalized:");
                for (Map.Entry<String, Set<String>> entry : buildData.getTags().entrySet())
                    logger.log("Key = " + entry.getKey() +
                            ", Value = " + entry.getValue());
            }
            // APM Traces
            logger.fine("End DatadogBuildListener#onFinalized");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        }
    }

    private String toTimeString(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%d min, %d sec", minutes, seconds);
    }


    public RunExt getRunExtForRun(WorkflowRun run) {
        return RunExt.create(run);
    }
}
